package com.crosswithfriends.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.crosswithfriends.app.databinding.ItemJournalEntryBinding
import com.crosswithfriends.app.databinding.LayoutHintSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.net.URLEncoder

class HintBottomSheetDialog : BottomSheetDialogFragment() {

    private var _binding: LayoutHintSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var journalManager: JournalManager
    private var currentResult: EncyclopediaResult? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutHintSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        journalManager = JournalManager(requireContext())

        setupTabs()
        setupSearch()
        setupActions()
    }

    private fun setupTabs() {
        binding.btnCloseSheet.setOnClickListener { dismiss() }

        binding.toggleTabs.check(R.id.btnTabLookup)
        binding.toggleTabs.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnTabLookup -> {
                        binding.viewLookup.visibility = View.VISIBLE
                        binding.viewJournal.visibility = View.GONE
                    }
                    R.id.btnTabJournal -> {
                        binding.viewLookup.visibility = View.GONE
                        binding.viewJournal.visibility = View.VISIBLE
                        loadSavedJournal()
                    }
                }
            }
        }
    }

    private fun setupSearch() {
        binding.etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performLookup()
                true
            } else false
        }

        binding.tilQuery.setEndIconOnClickListener {
            performLookup()
        }
    }

    private fun performLookup() {
        val query = binding.etQuery.text?.toString()?.trim() ?: ""
        if (query.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a word or clue to search", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressLookup.visibility = View.VISIBLE
        binding.cardResult.visibility = View.GONE

        lifecycleScope.launch {
            val result = EncyclopediaService.lookup(query)
            binding.progressLookup.visibility = View.GONE

            result.onSuccess { data ->
                currentResult = data
                displayResult(data)
            }.onFailure {
                // Friendly fallback with direct Google Search
                currentResult = EncyclopediaResult(
                    word = query,
                    category = "💡 General Trivia",
                    summary = "",
                    trivia = "Clue: \"$query\"\n\nTap 'Search on Google' below to view full crossword solver answers and web explanations.",
                    etymology = "",
                    example = "",
                    sourceUrl = "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")
                )
                displayResult(currentResult!!)
            }
        }
    }

    private fun displayResult(data: EncyclopediaResult) {
        binding.cardResult.visibility = View.VISIBLE
        binding.tvResultWord.text = data.word.uppercase()
        binding.tvResultCategory.text = data.category
        binding.tvResultTrivia.text = data.trivia

        if (data.etymology.isNotBlank()) {
            binding.tvResultEtymology.visibility = View.VISIBLE
            binding.tvResultEtymology.text = "Etymology: ${data.etymology}"
        } else {
            binding.tvResultEtymology.visibility = View.GONE
        }

        if (data.example.isNotBlank()) {
            binding.tvResultExample.visibility = View.VISIBLE
            binding.tvResultExample.text = "Example: \"${data.example}\""
        } else {
            binding.tvResultExample.visibility = View.GONE
        }

        when {
            data.category.contains("Geography") -> binding.chipGroupCategory.check(R.id.chipGeo)
            data.category.contains("Arts") -> binding.chipGroupCategory.check(R.id.chipArts)
            data.category.contains("Vocabulary") -> binding.chipGroupCategory.check(R.id.chipVocab)
            data.category.contains("Food") -> binding.chipGroupCategory.check(R.id.chipFood)
            data.category.contains("Science") -> binding.chipGroupCategory.check(R.id.chipScience)
        }
    }

    private fun setupActions() {
        binding.btnSaveToJournal.setOnClickListener {
            val data = currentResult ?: return@setOnClickListener
            val selectedCategory = getSelectedCategory(data.category)
            val userClue = binding.etClue.text?.toString()?.trim() ?: ""

            val entry = JournalEntry(
                word = data.word,
                category = selectedCategory,
                clue = userClue,
                trivia = data.trivia,
                etymology = data.etymology,
                example = data.example,
                sourceUrl = data.sourceUrl
            )

            journalManager.saveEntry(entry)
            Toast.makeText(requireContext(), "Saved '${entry.word}' to Journal! 📖", Toast.LENGTH_SHORT).show()
        }

        binding.btnSyncDriveSingle.setOnClickListener {
            val data = currentResult ?: return@setOnClickListener
            val selectedCategory = getSelectedCategory(data.category)
            val userClue = binding.etClue.text?.toString()?.trim() ?: ""

            val entry = JournalEntry(
                word = data.word,
                category = selectedCategory,
                clue = userClue,
                trivia = data.trivia,
                etymology = data.etymology,
                example = data.example,
                sourceUrl = data.sourceUrl
            )

            syncTextToGoogleDoc(entry.toGoogleDocSnippet(), "Crossword: ${entry.word}")
        }

        binding.btnGoogleSearch.setOnClickListener {
            val query = currentResult?.word ?: binding.etQuery.text?.toString() ?: ""
            val searchUrl = "https://www.google.com/search?q=" + URLEncoder.encode("$query crossword", "UTF-8")
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)))
        }

        binding.btnExportAllToDrive.setOnClickListener {
            val fullSnippet = journalManager.exportFullJournalForGoogleDocs()
            syncTextToGoogleDoc(fullSnippet, "My Crossword Learning Database")
        }

        binding.btnWebhookSettings.setOnClickListener {
            showWebhookSettingsDialog()
        }
    }

    private fun syncTextToGoogleDoc(content: String, title: String) {
        val webhookUrl = journalManager.getGoogleDocWebhookUrl()
        if (webhookUrl.isNotBlank()) {
            Toast.makeText(requireContext(), "Syncing directly to your Google Doc... ⏳", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val syncResult = journalManager.syncDirectToGoogleDoc(content, webhookUrl)
                syncResult.onSuccess {
                    Toast.makeText(requireContext(), "Synced directly to Google Doc! 📄✅", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(requireContext(), "Webhook failed. Opening Drive Share Sheet...", Toast.LENGTH_SHORT).show()
                    journalManager.shareToGoogleDriveOrDocs(content, title)
                }
            }
        } else {
            // First time: prompt user to configure direct webhook, or use share sheet
            AlertDialog.Builder(requireContext())
                .setTitle("Google Doc Direct Sync")
                .setMessage("To append entries directly into your Google Doc, set up a simple Google Apps Script Webhook URL. Would you like to enter your Webhook URL now, or share via standard Google Drive?")
                .setPositiveButton("Enter Webhook") { _, _ -> showWebhookSettingsDialog() }
                .setNegativeButton("Use Share Sheet") { _, _ -> journalManager.shareToGoogleDriveOrDocs(content, title) }
                .show()
        }
    }

    private fun showWebhookSettingsDialog() {
        val input = EditText(requireContext()).apply {
            hint = "https://script.google.com/macros/s/.../exec"
            setText(journalManager.getGoogleDocWebhookUrl())
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Google Doc Webhook URL")
            .setMessage("Paste your Google Apps Script Web App URL below to sync entries directly to your document in real time.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newUrl = input.text.toString().trim()
                journalManager.setGoogleDocWebhookUrl(newUrl)
                Toast.makeText(requireContext(), "Webhook URL saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getSelectedCategory(defaultCat: String): String {
        return when (binding.chipGroupCategory.checkedChipId) {
            R.id.chipGeo -> "🌍 Geography & Places"
            R.id.chipArts -> "🎨 Arts & Entertainment"
            R.id.chipVocab -> "📖 Vocabulary & Wordplay"
            R.id.chipFood -> "🍜 Food & Drink"
            R.id.chipScience -> "🧪 Science & Miscellaneous"
            else -> defaultCat
        }
    }

    private fun loadSavedJournal() {
        val entries = journalManager.getAllEntries()
        binding.tvJournalCount.text = "Saved Learning Entries (${entries.size})"
        binding.containerJournalEntries.removeAllViews()

        if (entries.isEmpty()) {
            binding.tvEmptyJournal.visibility = View.VISIBLE
        } else {
            binding.tvEmptyJournal.visibility = View.GONE
            val inflater = LayoutInflater.from(requireContext())

            entries.forEach { entry ->
                val itemBinding = ItemJournalEntryBinding.inflate(inflater, binding.containerJournalEntries, false)
                itemBinding.tvWord.text = entry.word.uppercase()
                itemBinding.tvCategory.text = entry.category

                if (entry.clue.isNotBlank()) {
                    itemBinding.tvClue.visibility = View.VISIBLE
                    itemBinding.tvClue.text = "Clue: ${entry.clue}"
                } else {
                    itemBinding.tvClue.visibility = View.GONE
                }

                itemBinding.tvTrivia.text = entry.trivia

                if (entry.etymology.isNotBlank()) {
                    itemBinding.tvEtymology.visibility = View.VISIBLE
                    itemBinding.tvEtymology.text = "Etymology: ${entry.etymology}"
                } else {
                    itemBinding.tvEtymology.visibility = View.GONE
                }

                if (entry.example.isNotBlank()) {
                    itemBinding.tvExample.visibility = View.VISIBLE
                    itemBinding.tvExample.text = "Example: \"${entry.example}\""
                } else {
                    itemBinding.tvExample.visibility = View.GONE
                }

                itemBinding.btnCopySnippet.setOnClickListener {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Crossword Entry", entry.toGoogleDocSnippet())
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "Copied formatted entry to clipboard!", Toast.LENGTH_SHORT).show()
                }

                itemBinding.btnDeleteEntry.setOnClickListener {
                    journalManager.deleteEntry(entry.id)
                    loadSavedJournal()
                    Toast.makeText(requireContext(), "Deleted '${entry.word}'", Toast.LENGTH_SHORT).show()
                }

                binding.containerJournalEntries.addView(itemBinding.root)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "HintBottomSheetDialog"
    }
}

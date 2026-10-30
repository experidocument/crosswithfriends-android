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
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
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
                Toast.makeText(requireContext(), "Could not fetch details. Tap 'Search on Google'.", Toast.LENGTH_SHORT).show()
                // Show empty fallback card with Google Search button
                currentResult = EncyclopediaResult(
                    word = query,
                    category = "💡 General Trivia",
                    summary = "",
                    trivia = "Search results could not be retrieved automatically.",
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

        // Auto-select category chip
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
            journalManager.shareToGoogleDriveOrDocs(entry.toGoogleDocSnippet(), "Crossword Clue: ${entry.word}")
        }

        binding.btnGoogleSearch.setOnClickListener {
            val query = currentResult?.word ?: binding.etQuery.text?.toString() ?: ""
            val searchUrl = "https://www.google.com/search?q=" + URLEncoder.encode("meaning of $query crossword", "UTF-8")
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)))
        }

        binding.btnExportAllToDrive.setOnClickListener {
            val fullSnippet = journalManager.exportFullJournalForGoogleDocs()
            journalManager.shareToGoogleDriveOrDocs(fullSnippet, "My Crossword Learning Database")
        }
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
                    Toast.makeText(requireContext(), "Copied to clipboard in Google Doc format!", Toast.LENGTH_SHORT).show()
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

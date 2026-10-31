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
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.crosswithfriends.app.databinding.DialogHelpCarouselBinding
import com.crosswithfriends.app.databinding.ItemHelpCardBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class HelpBottomSheetDialog : BottomSheetDialogFragment() {

    private var _binding: DialogHelpCarouselBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "HelpBottomSheetDialog"
        const val WEBHOOK_GUIDE_URL = "https://github.com/experidocument/crosswithfriends/blob/main/WEBHOOK_SETUP_GUIDE.md"
        const val SCRIPT_CODE = """function doPost(e) {
  try {
    var doc = DocumentApp.getActiveDocument();
    var body = doc.getBody();
    var data = JSON.parse(e.postData.contents);
    body.appendParagraph(data.text);
    return ContentService.createTextOutput("Success").setMimeType(ContentService.MimeType.TEXT);
  } catch (err) {
    return ContentService.createTextOutput("Error: " + err.message).setMimeType(ContentService.MimeType.TEXT);
  }
}"""
    }

    private val cards = listOf(
        HelpCard(
            emoji = "☕🧩",
            title = "Built on Pure Vibes!",
            badge = "CARD 1 OF 6",
            description = "This is a free, vibe-coded mobile companion crafted for crosswithfriends.com. It's not an official replica or commercial app — just a cozy, ad-free wrapper built to give you zen fullscreen puzzles and learning superpowers!"
        ),
        HelpCard(
            emoji = "🔒",
            title = "Google Sign-In Notice",
            badge = "CARD 2 OF 6",
            description = "Because this is a secure mobile web wrapper, direct 1-tap Google Sign-In is blocked by Google's policy. The pro move: Sign up or log in using your Email & Password directly (or set a password on your Cross with Friends account in a regular browser) to stay logged in here!"
        ),
        HelpCard(
            emoji = "💡",
            title = "The Magic Hint Button",
            badge = "CARD 3 OF 6",
            description = "Stuck on a tricky word or falling behind your friends? Tap the floating lightbulb in the bottom-right corner anytime to summon instant crossword assistance without leaving your puzzle."
        ),
        HelpCard(
            emoji = "🔤",
            title = "The 'Word' Mode",
            badge = "CARD 4 OF 6",
            description = "Fascinated by a word like 'LANAI', 'KLIMT', or 'UDON'? Search it in the Word tab to uncover rich trivia, origins, and etymology. Tap 'Save to Journal' to keep a permanent learning diary of cool facts you discover!"
        ),
        HelpCard(
            emoji = "🧩",
            title = "The 'Clue' Mode",
            badge = "CARD 5 OF 6",
            description = "Hit a dead end on a cryptic clue? Type the clue into the Clue tab. Our backend queries web search engines and returns an instant AI Overview of the plausible solution! Once you know the word, search it in the Word tab to save it."
        ),
        HelpCard(
            emoji = "📚☁️",
            title = "Your Learning Journal",
            badge = "CARD 6 OF 6",
            description = "All your saved words live in the Journal tab. Want them synced live into your personal Google Doc? Tap below to view our step-by-step 30-second Google Apps Script setup guide!",
            actionText = "📘 View Webhook Setup Guide",
            actionUrl = WEBHOOK_GUIDE_URL
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogHelpCarouselBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseHelp.setOnClickListener { dismiss() }

        val adapter = HelpAdapter(cards) { url ->
            showNativeWebhookGuide(url)
        }
        binding.viewPagerHelp.adapter = adapter

        binding.viewPagerHelp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator(position)
                binding.btnHelpPrev.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
                binding.btnHelpNext.text = if (position == cards.size - 1) "Let's Solve!" else "Next"
            }
        })

        binding.btnHelpPrev.setOnClickListener {
            val current = binding.viewPagerHelp.currentItem
            if (current > 0) {
                binding.viewPagerHelp.currentItem = current - 1
            }
        }

        binding.btnHelpNext.setOnClickListener {
            val current = binding.viewPagerHelp.currentItem
            if (current < cards.size - 1) {
                binding.viewPagerHelp.currentItem = current + 1
            } else {
                dismiss()
            }
        }
    }

    private fun showNativeWebhookGuide(onlineUrl: String) {
        val guideMessage = """
1. Open your Google Doc on computer or browser.
2. In the top menu, click Extensions > Apps Script.
3. Paste the 9-line script (tap 'Copy Code' below).
4. Click Deploy > New deployment > Web app:
   • Execute as: Me
   • Who has access: Anyone
5. Copy the generated Web app URL.
6. In this app's Journal tab, tap the Doc Settings (📖) icon and paste your URL!
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("📖 Google Doc Sync Setup")
            .setMessage(guideMessage)
            .setPositiveButton("📋 Copy Script Code") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Apps Script", SCRIPT_CODE)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Script copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Open GitHub Guide") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(onlineUrl)))
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun updateIndicator(position: Int) {
        val count = binding.layoutDots.childCount
        for (i in 0 until count) {
            val dot = binding.layoutDots.getChildAt(i) as ImageView
            dot.setImageResource(if (i == position) R.drawable.dot_active else R.drawable.dot_inactive)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class HelpAdapter(
        private val items: List<HelpCard>,
        private val onActionClick: (String) -> Unit
    ) : RecyclerView.Adapter<HelpAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemHelpCardBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemHelpCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvCardEmoji.text = item.emoji
            holder.binding.tvCardTitle.text = item.title
            holder.binding.tvCardBadge.text = item.badge
            holder.binding.tvCardBody.text = item.description

            if (item.actionText != null && item.actionUrl != null) {
                holder.binding.btnCardAction.visibility = View.VISIBLE
                holder.binding.btnCardAction.text = item.actionText
                holder.binding.btnCardAction.setOnClickListener { onActionClick(item.actionUrl) }
            } else {
                holder.binding.btnCardAction.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int = items.size
    }
}

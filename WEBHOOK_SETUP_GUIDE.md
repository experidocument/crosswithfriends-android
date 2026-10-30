# 📖 Google Doc Direct Sync Setup Guide

Connect your **Cross with Friends Android App** directly to your personal Google Doc so every saved clue, trivia fact, and etymology entry automatically appends in real time!

---

## ⚡ Quick 2-Minute Setup

### Step 1: Open Your Google Doc
Open your existing [Crossword Learning Database Google Doc](https://docs.google.com/document/d/1nO6rNf8vN8E0IHzfPOjWJFOmiB9qGIQGCcr_IpdPPc4/edit) (or create a new Google Doc).

---

### Step 2: Open Google Apps Script
In the top menu of your Google Doc:
1. Click **Extensions**
2. Click **Apps Script**

*(A new browser tab will open showing the code editor).*

```
Google Doc Menu:
File   Edit   View   Insert   Format   Tools   [ Extensions ]   Help
                                                     │
                                                     ├── [ Apps Script ]  <-- Click here!
```

---

### Step 3: Paste the Webhook Code
Delete any default code in the editor, and paste this exact 9-line script:

```javascript
function doPost(e) {
  try {
    var doc = DocumentApp.getActiveDocument();
    var body = doc.getBody();
    var data = JSON.parse(e.postData.contents);
    body.appendParagraph(data.text);
    return ContentService.createTextOutput("Success").setMimeType(ContentService.MimeType.TEXT);
  } catch (err) {
    return ContentService.createTextOutput("Error: " + err.message).setMimeType(ContentService.MimeType.TEXT);
  }
}
```

Click the **Save (💾)** icon at the top of the Apps Script editor.

---

### Step 4: Deploy as a Web App
1. In the top right corner of the Apps Script window, click the blue **Deploy** button.
2. Select **New deployment**.
3. Next to "Select type", click the **Gear (⚙️)** icon and choose **Web app**.
4. Configure these exact settings:
   * **Description**: `Crossword App Sync`
   * **Execute as**: **Me** (`your-email@gmail.com`)
   * **Who has access**: **Anyone** *(Important: This allows your mobile app to send entries directly without complex OAuth authentication)*.
5. Click **Deploy**.
6. If prompted, click **Authorize access**, choose your Google account, click **Advanced**, and click **Go to Untitled project (unsafe)**. *(This is standard for personal Google scripts you create yourself)*.

---

### Step 5: Copy Your Webhook URL
Once deployed, Google will give you a **Web app URL** that looks like this:

```
https://script.google.com/macros/s/AKfycbx.../exec
```

Copy this URL!

---

### Step 6: Paste into the App
1. In your **Cross with Friends** Android app, tap the **Hint (💡)** button.
2. Switch to the **📚 Journal** tab.
3. Tap the **Doc Settings (📖)** button in the top right.
4. Paste your Webhook URL and tap **Save**.

---

## 🎉 You're Done!
Whenever you look up a word and tap **"☁️ Sync Doc"** (or **"☁️ Sync All"** in the Journal), your entry appears live in your Google Doc formatted with clean bullet points:

```markdown
* LANAI
* Detailed Trivia: Lānaʻi is the sixth-largest of the Hawaiian Islands...
* Etymology: From the Hawaiian language meaning "Day of Conquest".
* Example: "We took a ferry to spend a quiet weekend on Lanai."
```

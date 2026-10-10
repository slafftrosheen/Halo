# Local models without hunting for files

Open **Settings → Intelligence → On device**. Search by name and tap **Sync Hugging Face** to discover downloadable LiteRT candidates. Halo keeps the saved catalog when offline and puts estimated fits for this phone first. Estimates do not mean a model has passed a test.

1. Download a model. Pause and resume if needed; public LiteRT downloads also have an Android notification.
2. For a licensed model, open its publisher license, accept it there and supply a read token. Halo does not save the token.
3. Tap **Test an answer**, then **Use for conversation** when ready. A download never changes your selected model by itself.
4. Open **Model roles** to explicitly choose conversation, quick replies, careful thinking, actions/tools or images. Some models cannot handle every role. Device tests and eligible choices remain separate from download estimates.

For a local voice, open **Voice → Speech output → Download Kokoro voice**. The complete English pack is acquired and validated inside Halo. Select Kokoro, then play and confirm the test phrase on the intended output. Other languages need a compatible voice provider. For listening, select a speech model, download it, choose it and test recognition. Interrupted speech downloads retain their files for the next explicit attempt.

**Advanced** and **Details** retain import, developer matrices, backend selection and technical diagnostics. The normal path does not require locating files on GitHub.

See [implementation and validation limits](../engineering/LOCAL-MODEL-LEVELUP-001.md).

package com.charles.crowdtransit.app.ui.assistant

/** Hopper's expressive states, driven by the assistant's engine/conversation state. */
enum class MascotState {
    /** Model not loaded — closed eyes, gentle "Z" drift. */
    Sleeping,

    /** Loaded and waiting — soft idle breathing bob and occasional blink. */
    Idle,

    /** Model is generating a reply — thinking wobble + animated ellipsis. */
    Thinking,

    /** Streaming tokens in — gentle talking bob synced to new text. */
    Talking,

    /** Composer focused / voice-style prompt — leans in attentively. */
    Listening,

    /** Something needs attention (e.g. "your stop is next") — accent-tinted pop. */
    Alert,
}

package kr.univer.bizlog.gateway

/** 음성 경계. 실패하면 조용히 null/false를 돌려주고, 화면이 버튼으로 대신한다. */
interface VoiceGateway {
    /** 다 말할 때까지 기다린다. 말하지 못했으면 false. */
    suspend fun speak(text: String): Boolean
    /** 한 번 듣고 최종 문장을 돌려준다. 못 들었으면 null. */
    suspend fun listenOnce(): String?
    fun release()
}

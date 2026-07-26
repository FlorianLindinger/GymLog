package com.gymlog.app.data

/** A compact, short-lived record of rows removed by a single user action. */
data class DeletionJournal internal constructor(internal val payload: String)

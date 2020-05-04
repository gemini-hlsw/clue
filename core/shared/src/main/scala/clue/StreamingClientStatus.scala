package clue

sealed trait StreamingClientStatus
object StreamingClientStatus {
  final case object Connecting extends StreamingClientStatus
  final case object Open       extends StreamingClientStatus
  final case object Closing    extends StreamingClientStatus
  final case object Closed     extends StreamingClientStatus
}

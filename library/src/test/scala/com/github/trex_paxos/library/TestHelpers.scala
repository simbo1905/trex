package com.github.trex_paxos.library

import Ordering._

import scala.collection.immutable.{SortedMap, TreeMap}

case class DummyCommandValue(id: String) extends CommandValue {
  override def bytes: Array[Byte] = Array()

  override def msgUuid: String = id
}

object DummyRemoteRef {
  def apply(number: Int = 0) = s"DummyRemoteRef$number"
}

class UndefinedIO extends PaxosIO{
  override def journal: Journal = throw new AssertionError("deliberately not implemented")

  override def logger: PaxosLogging = throw new AssertionError("deliberately not implemented")

  override def randomTimeout: Long = throw new AssertionError("deliberately not implemented")

  override def clock(): Long = throw new AssertionError("deliberately not implemented")

  override def send(msg: PaxosMessage): Unit = throw new AssertionError("deliberately not implemented")

  override def deliver(payload: Payload): Any = throw new AssertionError("deliberately not implemented")

  override def associate(value: CommandValue, id: Identifier): Unit = throw new AssertionError("deliberately not implemented")

  override def respond(results: Option[Map[Identifier, Any]]): Unit = throw new AssertionError("deliberately not implemented")
}

trait SilentLogging {
  this: UndefinedIO =>
  override def logger: PaxosLogging = NoopPaxosLogging
}

class UndefinedPrepareResponse extends PrepareResponse {
  override def requestId: Identifier = throw new AssertionError("deliberately not implemented")

  override def highestAcceptedIndex: Long = throw new AssertionError("deliberately not implemented")

  override def leaderHeartbeat: Long = throw new AssertionError("deliberately not implemented")

  override def progress: Progress = throw new AssertionError("deliberately not implemented")

  override def from: Int = throw new AssertionError("deliberately not implemented")

  override def to: Int = throw new AssertionError("deliberately not implemented")
}

class UndefinedJournal extends Journal {
  override def saveProgress(progress: Progress): Unit = throw new AssertionError("deliberately not implemented")

  override def bounds(): JournalBounds = throw new AssertionError("deliberately not implemented")

  override def loadProgress(): Progress = throw new AssertionError("deliberately not implemented")

  override def accepted(logIndex: Long): Option[Accept] = throw new AssertionError("deliberately not implemented")

  override def accept(a: Accept*): Unit = throw new AssertionError("deliberately not implemented")
}

case class MessageAndTimestamp(msg: PaxosMessage, ts: Long)

class TestIO(j: Journal) extends UndefinedIO {
  val sent = Box(Seq[MessageAndTimestamp]())

  override def send(msg: PaxosMessage): Unit = {
    sent(sent() :+ MessageAndTimestamp(msg, System.nanoTime))
  }

  override def journal: Journal = j

  override def randomTimeout: Long = 12345L

  override def logger: PaxosLogging = NoopPaxosLogging

  override def respond(results: Option[Map[Identifier, Any]]): Unit = {}
}

class UndefinedAcceptResponse extends AcceptResponse {
  override def requestId: Identifier = throw new AssertionError("deliberately not implemented")

  override def from: Int = throw new AssertionError("deliberately not implemented")

  override def progress: Progress = throw new AssertionError("deliberately not implemented")

  override def to: Int = throw new AssertionError("deliberately not implemented")
}

case class TimeAndParameter(time: Long, parameter: Any)

object TestHelpers extends PaxosLenses{

  val minPrepare = Prepare(Identifier(0, BallotNumber(0, 0), 0  ))

  val undefinedIO = new UndefinedIO

  val undefinedSilentIO = new UndefinedIO with SilentLogging

  val undefinedIOwithNoopLogging = new UndefinedIO {
    override def logger: PaxosLogging = NoopPaxosLogging
  }

  val noopJournal = new Journal {
    override def saveProgress(progress: Progress): Unit = {}

    override def bounds(): JournalBounds = JournalBounds(0L, 0L)

    override def loadProgress(): Progress = TestHelpers.initialData.progress

    override def accepted(logIndex: Long): Option[Accept] = None

    override def accept(a: Accept*): Unit = {}
  }

  val negativeClockIO = new UndefinedIO {
    override def clock(): Long = Long.MinValue
  }

  val maxClockIO = new UndefinedIO {
    override def clock(): Long = Long.MaxValue
  }

  val lowValue = 1

  val initialData = PaxosData(progress = Progress(
          highestPromised = BallotNumber(lowValue, lowValue),
          highestCommitted = Identifier(from = 0, number = BallotNumber(lowValue, lowValue), logIndex = 0)
  ), leaderHeartbeat = 0, timeout = 0, prepareResponses = TreeMap(), epoch = None, acceptResponses = TreeMap() )

  val initialQuorumStrategy = new DefaultQuorumStrategy(() => 3)

  val initialQuorumStrategy4 = new DefaultQuorumStrategy(() => 4)

  val initialQuorumSimpleStrategy4 = new SimplyMajorityQuorumStrategy(() => 4)

  val initialQuorumStrategy5 = new DefaultQuorumStrategy(() => 5)

  val undefinedPrepareResponse = new UndefinedPrepareResponse

  val undefinedAcceptResponse = new UndefinedAcceptResponse

  val identifier98: Identifier = Identifier(1, BallotNumber(2, 2), 98L)

  val a98 = Accept(identifier98, NoOperationCommandValue)

  val emptyAcceptResponses98: SortedMap[Identifier, AcceptResponsesAndTimeout] = TreeMap(
    a98.id -> AcceptResponsesAndTimeout(100L, a98, Map.empty)
  )

  val prepare = Prepare(Identifier(0, BallotNumber(lowValue + 1, 0), 1L))

  val prepareSelfVotes = SortedMap.empty[Identifier, Map[Int, PrepareResponse]] ++
    Seq((prepare.id -> Map(0 -> PrepareAck(prepare.id, 0, initialData.progress, 0, 0, None))))

  val identifier99: Identifier = Identifier(2, BallotNumber(2, 2), 99L)
  val identifier100: Identifier = Identifier(3, BallotNumber(3, 3), 100L)
  val a99 = Accept(identifier99, NoOperationCommandValue)

  val a100 = Accept(identifier100, DummyCommandValue("100"))

  val emptyAcceptResponses: SortedMap[Identifier, AcceptResponsesAndTimeout] = TreeMap(
    a98.id -> AcceptResponsesAndTimeout(100L, a98, Map.empty),
    a99.id -> AcceptResponsesAndTimeout(50L, a99, Map.empty),
    a100.id -> AcceptResponsesAndTimeout(150L, a100, Map.empty)
  )

  val highestPromisedHighestCommittedLens = progressLens andThen Progress.highestPromisedHighestCommitted

  val accepts98thru100 = Seq(a98, a99, a100)

  def journaled98thru100(logIndex: Long): Option[Accept] = {
    logIndex match {
      case 98L => Option(a98)
      case 99L => Option(a99)
      case 100L => Option(a100)
      case _ => None
    }
  }

  val identifier101: Identifier = Identifier(3, BallotNumber(3, 3), 101L)

  def journaled98thru101(logIndex: Long): Option[Accept] = {
    logIndex match {
      case 98L => Option(a98)
      case 99L => Option(a99)
      case 100L => Option(a100)
      case 101L => Option(a101)
      case _ => None
    }
  }

  val initialDataCommittedSlotOne = PaxosData(
    Progress(
      BallotNumber(lowValue, lowValue), Identifier(0, BallotNumber(lowValue, lowValue), 1)
    ), 0, 0, TreeMap(), None, TreeMap()
  )

  val a101 = Accept(identifier101, DummyCommandValue("101"))

  val misorderedAccepts = Seq(a98, a99, a101, a100)

  val progress96 = Progress(BallotNumber(0, 0), Identifier(0, BallotNumber(0, 0), 96L))

  val progress97 = Progress(BallotNumber(0, 0), Identifier(0, BallotNumber(0, 0), 97L))

  val progress98 = Progress(BallotNumber(0, 0), Identifier(0, BallotNumber(0, 0), 98L))

  val zeroProgress = Progress(BallotNumber(0, 0), Identifier(0, BallotNumber(0, 0), 0L))

  def progressWith(promise: BallotNumber, committed: BallotNumber) = zeroProgress.copy(highestPromised = promise,
    highestCommitted = zeroProgress.highestCommitted.copy(number = committed))

  val timedOutAt100AcceptResponses: SortedMap[Identifier, AcceptResponsesAndTimeout] = TreeMap(
    a98.id -> AcceptResponsesAndTimeout(100L, a98, Map.empty),
    a99.id -> AcceptResponsesAndTimeout(50L, a99, Map.empty)
  )

  val recoverHighPrepare = Prepare(Identifier(0, BallotNumber(lowValue + 1, 0), 1L))
  val recoverHighPrepare2 = Prepare(Identifier(0, BallotNumber(lowValue + 1, 0), 2L))

  val pNack = PrepareNack(recoverHighPrepare.id, 0, initialData.progress, 0, 0)

  val highPrepareEpoch = Some(recoverHighPrepare.id.number)
  val prepareSelfNack = SortedMap.empty[Identifier, Map[Int, PrepareResponse]] ++
    Seq((recoverHighPrepare.id -> Map(0 -> pNack)))
  val selfNackPrepares = initialData.copy(epoch = highPrepareEpoch, prepareResponses = prepareSelfNack, acceptResponses = SortedMap.empty)

  val pAck = PrepareAck(recoverHighPrepare.id, 0, initialData.progress, 0, 0, None)

  val prepareSelfAck = SortedMap.empty[Identifier, Map[Int, PrepareResponse]] ++
    Seq((recoverHighPrepare.id -> Map(0 -> pAck)))
  val selfAckPrepares = initialData.copy(epoch = highPrepareEpoch, prepareResponses = prepareSelfAck, acceptResponses = SortedMap.empty)

  val prepareSelfAck2 = SortedMap.empty[Identifier, Map[Int, PrepareResponse]] ++
    Seq(
      (recoverHighPrepare.id -> Map(0 -> PrepareAck(recoverHighPrepare.id, 0, initialData.progress, 0, 0, None))),
      (recoverHighPrepare2.id -> Map(0 -> PrepareAck(recoverHighPrepare2.id, 0, initialData.progress, 0, 0, None)))
    )
  val selfAckPrepares2 = initialData.copy(epoch = highPrepareEpoch, prepareResponses = prepareSelfAck2, acceptResponses = SortedMap.empty)

  val initialData97 = PaxosData(
    progress = Progress(
      highestPromised = BallotNumber(lowValue, lowValue),
      highestCommitted = Identifier(from = 0, number = BallotNumber(lowValue, lowValue), logIndex = 97)
    ),
    leaderHeartbeat = 0,
    timeout = 0,
    prepareResponses = TreeMap(), epoch = None, acceptResponses = TreeMap()
  )

  val initialData96 = initialData97.copy(progress = progress96)

  val initialData98 = initialData97.copy(progress = progress98)

  val a98ack0 = AcceptAck(a98.id, 0, initialData97.progress)
  val a98ack1 = AcceptAck(a98.id, 1, initialData97.progress)
  val a98ack3 = AcceptAck(a98.id, 3, initialData97.progress)

  val a98nack0 = AcceptNack(a98.id, 0, initialData97.progress)
  val a98nack1 = AcceptNack(a98.id, 1, initialData97.progress)
  val a98nack2 = AcceptNack(a98.id, 2, initialData97.progress)

  val a99ack0 = AcceptAck(a99.id, 0, initialData97.progress)
  val a99ack1 = AcceptAck(a99.id, 1, initialData97.progress)
  val a99ack2 = AcceptAck(a99.id, 1, initialData97.progress)
  val a99nack1 = AcceptNack(a99.id, 1, initialData97.progress)

  val a98ackProgress98 = AcceptAck(a98.id, 1, progress98)

  val acceptSelfAck98 = SortedMap.empty[Identifier, AcceptResponsesAndTimeout] ++
    Seq((a98.id -> AcceptResponsesAndTimeout(Long.MaxValue, a98, Map(0 -> a98ack0))))

  val acceptSplitAckAndNack = SortedMap.empty[Identifier, AcceptResponsesAndTimeout] ++
    Seq((a98.id -> AcceptResponsesAndTimeout(Long.MaxValue, a98, Map(0 -> a98ack0, 1 -> a98nack1)))
    )

  val acceptkAndTwoNack98 = SortedMap.empty[Identifier, AcceptResponsesAndTimeout] ++
    Seq((a98.id -> AcceptResponsesAndTimeout(Long.MaxValue, a98, Map(0 -> a98ack0, 1 -> a98nack1, 2 -> a98nack2)))
    )

  val acceptSelfAck98and99 = SortedMap.empty[Identifier, AcceptResponsesAndTimeout] ++
    Seq((a98.id -> AcceptResponsesAndTimeout(Long.MaxValue, a98, Map(0 -> a98ack0))),
      (a99.id -> AcceptResponsesAndTimeout(Long.MaxValue, a99, Map(0 -> a99ack0))))

  val acceptAck98and99empty = SortedMap.empty[Identifier, AcceptResponsesAndTimeout] ++
    Seq((a98.id -> AcceptResponsesAndTimeout(Long.MaxValue, a98, Map(0 -> a98ack0))),
      (a99.id -> AcceptResponsesAndTimeout(Long.MaxValue, a99, Map.empty)))

  val initialDataClientCommand = Map(initialData.progress.highestCommitted -> (NoOperationCommandValue, DummyRemoteRef()))

  val expectedString = "Knossos"
  val expectedBytes = expectedString.getBytes
}

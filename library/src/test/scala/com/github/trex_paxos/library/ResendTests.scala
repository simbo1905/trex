package com.github.trex_paxos.library

import org.scalamock.scalatest.MockFactory
import org.scalatest._
import matchers.should._

import scala.collection.immutable.{SortedMap, TreeMap}
import scala.collection.mutable.ArrayBuffer

class TestResendHandler extends ResendHandler {
  override def highestNumberProgressed(data: PaxosData): BallotNumber = throw new AssertionError("deliberately not implemented")
}

class ResendAcceptsTests extends wordspec.AnyWordSpec with Matchers with MockFactory {

  import TestHelpers._

  "ResendHandler" should {
    "find the timed-out accepts in" in {
      val timedout = ResendHandler.timedOutResponse(100L, emptyAcceptResponses)
      timedout shouldBe timedOutAt100AcceptResponses
    }
    "detect highest own promise" in {
      val highest = ResendHandler.highestPromise(BallotNumber(4, 4), emptyAcceptResponses)
      highest shouldBe BallotNumber(4, 4)
    }
    "detect others highest promise" in {
      val highPromise = emptyAcceptResponses +
        (a99.id -> AcceptResponsesAndTimeout(50L, a99, Map(0 -> AcceptNack(a99.id, 0, progressWith(BallotNumber(99, 99), zeroProgress.highestPromised)))))
      val highest = ResendHandler.highestPromise(BallotNumber(1, 1), highPromise)
      highest shouldBe BallotNumber(99, 99)
    }
    "detect others highest committed" in {
      val highPromise = emptyAcceptResponses +
        (a99.id -> AcceptResponsesAndTimeout(50L, a99, Map(0 -> AcceptNack(a99.id, 0, progressWith(zeroProgress.highestPromised, BallotNumber(99, 99))))))
      val highest = ResendHandler.highestPromise(BallotNumber(1, 1), highPromise)
      highest shouldBe BallotNumber(99, 99)
    }
    "refresh accepts" in {
      val newNumber = BallotNumber(4, 4)
      val refreshed = ResendHandler.refreshAccepts(newNumber, Seq(a98, a99, a100))
      val identifier98: Identifier = Identifier(1, newNumber, 98L)
      val identifier99: Identifier = Identifier(2, newNumber, 99L)
      val identifier100: Identifier = Identifier(3, newNumber, 100L)
      refreshed shouldBe Seq(a98.copy(id = identifier98), a99.copy(id = identifier99), a100.copy(id = identifier100))
    }
    "sets a new timeout per accept on resend with same epoch" in {
      // given
      val handler = new TestResendHandler
      // when
      val AcceptsAndData(accepts, data) = handler.computeResendAccepts(new TestIO(new UndefinedJournal) {
        override def randomTimeout: Long = 121L
      }, PaxosAgent(99, Leader, initialData.copy(acceptResponses = emptyAcceptResponses), initialQuorumStrategy), 100L)
      // then
      data.timeout shouldBe 121L
      accepts.size shouldBe 2
      data.acceptResponses.values.map(_.timeout) shouldBe Seq(121L, 121L, 150L)
    }
    "goes to a one higher epoch on detecting higher promise in responses" in {
      // given
      val handler = new TestResendHandler
      val higherPromise = emptyAcceptResponses +
        (a99.id -> AcceptResponsesAndTimeout(50L, a99, Map(0 -> AcceptNack(a99.id, 0, progressWith(zeroProgress.highestPromised, BallotNumber(99, 99))))))
      val newEpoch = BallotNumber(100, 100)
      // when
      val AcceptsAndData(accepts, data) = handler.computeResendAccepts(new TestIO(new UndefinedJournal) {
        override def randomTimeout: Long = 121L
      }
        , PaxosAgent(100, Leader, initialData.copy(acceptResponses = higherPromise), initialQuorumStrategy), 100L)
      // then it move to the 1-higher epoch
      data.epoch match {
        case Some(newEpoch) => // success
        case x => fail(s"$x is not expected $newEpoch")
      }
      // has two messages to resend
      accepts.size shouldBe 2
      // then name the new epoch
      accepts.map(_.id.number).foreach {
        _ match {
          case `newEpoch` => // success
          case x => fail(s"$x is not expected $newEpoch")
        }
      }
      // top level guard timeout is set
      data.timeout shouldBe 121L
      // the new epoch accepts are listed as the values we are awaiting
      data.acceptResponses.values.map(_.accept).filter(_.id.number == newEpoch) shouldBe accepts
      // we have made to self acks to the two new higher accepts
      data.acceptResponses.values.flatMap(_.responses.values).filter(_.isInstanceOf[AcceptAck]).size shouldBe 2
    }
    "sets a new timeout per accept on resend new epoch" in {
      // given
      val handler = new TestResendHandler
      val higherPromise = emptyAcceptResponses +
        (a99.id -> AcceptResponsesAndTimeout(50L, a99, Map(0 -> AcceptNack(a99.id, 0, progressWith(zeroProgress.highestPromised, BallotNumber(99, 99))))))
      // when
      val AcceptsAndData(accepts, data) = handler.computeResendAccepts(new TestIO(new UndefinedJournal) {
        override def randomTimeout: Long = 121L
      }, PaxosAgent(99, Leader, initialData.copy(acceptResponses = higherPromise), initialQuorumStrategy), 100L)
      // then
      data.timeout shouldBe 121L
      accepts.size shouldBe 2
      data.acceptResponses.values.map(_.timeout) shouldBe Seq(121L, 121L, 150L)
    }
    "journalling and sending happens in the correct order" in {
      // given a journal which records saving and accepting
      val saveJournalTime = Box(0L)
      val acceptJournalTime = Box(0L)
      val tempJournal = new UndefinedJournal {
        override def saveProgress(progress: Progress): Unit = saveJournalTime(System.nanoTime())

        override def accept(a: Accept*): Unit = acceptJournalTime(System.nanoTime())
      }
      // and a handler that records broadcase time
      val sendTime = Box(0L)
      val handler = new TestResendHandler
      // when we get it to do work

      handler.handleResendAccepts(new TestIO(tempJournal) {
        override def randomTimeout: Long = 121L

        override def send(msg: PaxosMessage): Unit = sendTime(System.nanoTime())
      }, PaxosAgent(99, Leader, initialData.copy(acceptResponses = emptyAcceptResponses), initialQuorumStrategy), 100L)
      // then we saved, accepted and sent
      assert(saveJournalTime() > 0 && acceptJournalTime() > 0 && sendTime() > 0)
      // in the correct order had we done a full round of paxos which is promise, accept then send last
      assert(sendTime() > acceptJournalTime() && acceptJournalTime() > saveJournalTime())
    }
    "resend prepares and refresh its timeout" in {
      // given
      val agent = PaxosAgent(0, Follower, selfAckPrepares2, initialQuorumStrategy)
      val handler = new ResendHandler{}
      val sent = ArrayBuffer[PaxosMessage]()
      val ioWithTimeout = new UndefinedIO with SilentLogging {
        override def randomTimeout: Long = 12345L

        override def send(msg: PaxosMessage): Unit = sent += msg
      }
      // when
      val PaxosAgent(_, _, data, _) = handler.handleResendPrepares(ioWithTimeout, agent, selfAckPrepares.timeout + 1)
      // then
      data.timeout shouldBe 12345L
      sent.size shouldBe 2
      val prepares: ArrayBuffer[Option[Prepare]] = sent.collect {
        case p: Prepare => Some(p)
        case _ => None
      }
      val sentPrepareIds = prepares.flatten.map(_.id)
      val expectedPrepareIds = selfAckPrepares2.prepareResponses.values.flatMap(_.values.map(_.requestId))
      sentPrepareIds shouldBe expectedPrepareIds
    }
    "does not timeout on accepts which have not timed-out" in {
      // given
      val timeoutAt50 = 50L
      val timeNow = 49L
      val handler = new TestResendHandler
      val higherPromise = emptyAcceptResponses +
        (a99.id -> AcceptResponsesAndTimeout(timeoutAt50, a99, Map(0 -> AcceptNack(a99.id, 0, progressWith(zeroProgress.highestPromised, BallotNumber(99, 99))))))
      val agent = PaxosAgent(100, Leader, initialData.copy(acceptResponses = higherPromise), initialQuorumStrategy)

      // when
      val AcceptsAndData(accepts, data) = handler.computeResendAccepts(undefinedIO, agent, timeNow)
      // then
      data shouldBe agent.data
      accepts.isEmpty shouldBe true

    }
  }
}

object ResendAcceptsTests {

  import Ordering._

  val identifier98: Identifier = Identifier(1, BallotNumber(1, 1), 98L)
  val identifier99: Identifier = Identifier(2, BallotNumber(2, 2), 99L)
  val identifier100: Identifier = Identifier(3, BallotNumber(3, 3), 100L)

  val a98 = Accept(identifier98, NoOperationCommandValue)
  val a99 = Accept(identifier99, NoOperationCommandValue)
  val a100 = Accept(identifier100, NoOperationCommandValue)

  val emptyAcceptResponses: SortedMap[Identifier, AcceptResponsesAndTimeout] = TreeMap(
    a98.id -> AcceptResponsesAndTimeout(100L, a98, Map.empty),
    a99.id -> AcceptResponsesAndTimeout(50L, a99, Map.empty),
    a100.id -> AcceptResponsesAndTimeout(120L, a100, Map.empty)
  )
}

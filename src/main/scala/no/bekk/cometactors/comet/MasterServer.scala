package no.bekk.cometactors.comet

import net.liftweb.actor.LiftActor
import collection.mutable.HashMap
import net.liftweb.http.{RemoveAListener, AddAListener, CometActor, ListenerManager}
import net.liftweb.util.ClearNodes
import net.liftweb.http.js.JE.Call
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.js.JsCmd

object CaseLockMasterServer extends LiftActor {
  type LM = MyListenerManager with LiftActor
  type CA = CaseLockCometActor

  private val servers = new HashMap[Int, LM]()

  def createServer(cometActor: CA): LM = new CaseLockServer(cometActor.caseIdent)
  def key(cometActor: CA): Int = 0
  def key(listenerManager: LM): Int = 0
  def get(key: Int) = servers.get(key)

  protected def messageHandler: PartialFunction[Any, Unit] = {
    case addAListener @ AddAListener(cometActor: CA, _) =>
			val op = createServer(cometActor)
			val server = servers.getOrElseUpdate(key(cometActor), op)
			server ! addAListener

    case removeAListenerMsg @ RemoveAListener(cometActor: CA) =>
			servers.get(key(cometActor)).foreach(_ ! removeAListenerMsg)

    case ServerListenersListEmptied(lm: LM) =>
			servers.remove(key(lm))

    case lc @ LockCase(caseIdent, _) =>
			get(caseIdent).foreach(server => server ! lc)

    case ulc @ UnLockCase(caseIdent, _) =>
			get(caseIdent).foreach(server => server ! ulc)
  }
}

trait MyListenerManager extends ListenerManager {
  self: LiftActor with MyListenerManager =>

  def caseIdent: Int

  var done = false

  def masterServer: LiftActor

  override protected def onListenersListEmptied() {
		masterServer ! ServerListenersListEmptied(this)
		done = true
	}
}

class CaseLockServer(val caseIdent: Int) extends LiftActor with MyListenerManager{

  lazy val currentCase = Case.cases(caseIdent)

  def masterServer = CaseLockMasterServer

  protected def createUpdate = {
    CaseLockCometRegisteredMessage(currentCase)
  }
}

class CaseLockCometActor extends CometActor {
  lazy val caseIdent = name.open_!.split("_")(0).toInt
  lazy val userIdent = name.open_!.split("_")(1).toInt
  var currentCase: Option[Case] = None

  override def lowPriority = {
    case CaseLockCometRegisteredMessage(cc) => {
      currentCase = Option(cc)
      reRender()
    }
    case LockCase(ci, ui) => {
      val lockStatusJs: JsCmd = currentCase match {
        case Some(c) if c.lockedBy.exists(_ == userIdent) => Call("LockTracker.greenLock")
        case Some(c) if c.lockedBy.nonEmpty => Call("LockTracker.redLock", c.lockedBy.get)
        case Some(c) => Call("LockTracker.openLock")
        case None => Noop
      }
      partialUpdate(lockStatusJs)
    }
  }

  def render = {

    ClearNodes
  }
}


// MESSAGES

case class LockCase(caseIdent: Int, userIdent: Int)
case class UnLockCase(caseIdent: Int, userIdent: Int)
case class CaseLockCometRegisteredMessage(currentCase: Case)
sealed case class ServerListenersListEmptied(listenerManager: ListenerManager)

case class Case(ident: Int, asset: String, var lockedBy: Option[Int])
object Case {
  val cases = Map(1 -> Case(1, "Bil", None), 2 -> Case(2, "Båt", None), 3 -> Case(3, "Fly", None))
}
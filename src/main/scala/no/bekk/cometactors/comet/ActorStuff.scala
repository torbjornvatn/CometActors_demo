package no.bekk.cometactors.comet

import net.liftweb.actor.LiftActor
import collection.mutable.HashMap
import net.liftweb.http.js.JE.Call
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.js.JsCmd
import net.liftweb.util.PassThru
import net.liftweb.http._
import net.liftweb._
import util.Helpers._
import xml.{Text, NodeSeq}

object CaseLockMasterServer extends LiftActor {
  type LM = MyListenerManager with LiftActor
  type CA = CaseLockCometActor

  private val servers = new HashMap[Int, LM]()

  def createServer(cometActor: CA): LM = new CaseLockServer(cometActor.caseIdent)
  def key(cometActor: CA): Int = cometActor.caseIdent
  def key(listenerManager: LM): Int = listenerManager.caseIdent
  def get(key: Int) = servers.get(key)

  protected def messageHandler: PartialFunction[Any, Unit] = {
    case addAListener @ AddAListener(cometActor: CA, _) =>
			val op = createServer(cometActor)
			val server = servers.getOrElseUpdate(key(cometActor), op)
      println("Adds listener: "+cometActor.userIdent+" - "+server.caseIdent)
			server ! addAListener

    case removeAListenerMsg @ RemoveAListener(cometActor: CA) =>
			servers.get(key(cometActor)).foreach(_ ! removeAListenerMsg)

    case ServerListenersListEmptied(lm: LM) =>
			servers.remove(key(lm))

    case lc @ LockCase(casen) => {
      println("Master got lock msg: "+casen.ident+" servers: "+ servers(1))
			get(casen.ident).foreach(server => server ! lc)
    }

    case ulc @ UnLockCase(casen) => {
      println("Master got unlock msg")
			get(casen.ident).foreach(server => server ! ulc)
    }
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

class CaseLockServer(val caseIdent: Int) extends LiftActor with MyListenerManager {

  def masterServer = CaseLockMasterServer

  protected def createUpdate = {
    CaseLockCometRegisteredMessage(Case.cases(caseIdent))
  }

  override def lowPriority = {
    case lc: LockCase => {
      updateListeners(lc)
    }

    case ulc: UnLockCase => {
      updateListeners(ulc)
    }
  }
}

class CaseLockCometActor extends CometActor with CometListener {
  lazy val caseIdent = name.open_!.split("_")(0).toInt
  lazy val userIdent = name.open_!.split("_")(1).toInt
  var currentCase: Option[Case] = None

 	private lazy val lockServer = CaseLockMasterServer
	override def registerWith = lockServer

  override def lowPriority = {
    case CaseLockCometRegisteredMessage(cc) => {
      currentCase = Option(cc)
      reRender()
    }
    case LockCase(casen) if (casen.ident == caseIdent) => {
      currentCase = Some(casen)
      val lockStatusJs: JsCmd = casen match {
        case c: Case if c.lockedBy.exists(_ == userIdent) => Call("LockTracker.greenLock", uniqueId)
        case c: Case => Call("LockTracker.redLock", uniqueId)
      }
      println("Lock js: "+lockStatusJs)
      partialUpdate(lockStatusJs)
    }

    case UnLockCase(casen) if (casen.ident == caseIdent) => {
      currentCase = Some(casen)
      var lockStatusJs: JsCmd = casen match {
        case c: Case => Call("LockTracker.openLock", uniqueId)
      }
      println("Unlock js: "+lockStatusJs)
      partialUpdate(lockStatusJs)
    }
  }

  def toggleLock = {
    currentCase.foreach(c =>{
      println("toggled case: "+c.ident+" - "+ userIdent)
      if(c.isLocked){
        c.lockedBy = None
        lockServer ! UnLockCase(c)
      } else {
        c.lockedBy = Some(userIdent)
        lockServer ! LockCase(c)
      }
    })
    Noop
  }

  def render = {
    	"#locked *" #> SHtml.ajaxButton(<img src="images/Padlock-red.svg"></img>, () => Noop) &
			"#lockButton *" #> SHtml.ajaxButton(<img src="images/Open_Padlock.svg"></img>, () => toggleLock) &
			"#unlockButton *" #> SHtml.ajaxButton(<img src="images/Padlock-green.svg"></img>, () => toggleLock)
  }
}


// MESSAGES

case class LockCase(casen: Case)
case class UnLockCase(casen: Case)
case class CaseLockCometRegisteredMessage(currentCase: Case)
sealed case class ServerListenersListEmptied(listenerManager: ListenerManager)

case class Case(ident: Int, asset: String, var lockedBy: Option[Int]) {
  def isLocked = lockedBy.nonEmpty
}
object Case {
  val cases = Map(1 -> Case(1, "Bil", None), 2 -> Case(2, "Båt", None), 3 -> Case(3, "Fly", None))
}
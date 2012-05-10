package no.bekk.cometactors.comet

import net.liftweb.actor.LiftActor
import collection.mutable.HashMap
import net.liftweb.http.js.JE.Call
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.js.JsCmd
import net.liftweb.http._
import scala.None

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
			server ! addAListener

    case removeAListenerMsg @ RemoveAListener(cometActor: CA) =>
			servers.get(key(cometActor)).foreach(_ ! removeAListenerMsg)

    case ServerListenersListEmptied(lm: LM) =>
			servers.remove(key(lm))

    case lc @ LockCase(casen) => {
			get(casen.ident).foreach(server => server ! lc)
    }

    case ulc @ UnLockCase(casen) => {
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

  val  updateInfo  = AdditionalInfoServer ! _


  override def lowPriority = {
    case lc: LockCase => {
      updateListeners(lc)
      updateInfo(SetInfo(lc.info))
    }

    case ulc: UnLockCase => {
      updateListeners(ulc)
      updateInfo(SetInfo(ulc.info))
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
      val lockStatusJs: JsCmd = casen match {
        case c: Case => Call("LockTracker.openLock", uniqueId)
      }
      partialUpdate(lockStatusJs)
    }
  }

  def toggleLock = {
    currentCase.foreach(c =>{
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

case class LockCase(casen: Case){
  def info = (casen.ident, "Saken er låst av: %s" format (casen.lockedBy.get))
}
case class UnLockCase(casen: Case) {
  def info = (casen.ident, "Saken er låst opp")
}
case class CaseLockCometRegisteredMessage(currentCase: Case)
sealed case class ServerListenersListEmptied(listenerManager: ListenerManager)

case class Case(ident: Int, asset: String, var lockedBy: Option[Int]) {
  def isLocked = lockedBy.nonEmpty
}
object Case {
  val cases = Map(1 -> Case(1, "Bil", None), 2 -> Case(2, "Båt", None), 3 -> Case(3, "Fly", None))
}


// Case lock server view



case class SetInfo(info: (Int, String))

object AdditionalInfoServer extends LiftActor {
  private val infoActors = new HashMap[Int, CaseLockServerInfoCometActor]

  protected def messageHandler : PartialFunction[Any, Unit] = {
     case addAListener @ AddAListener(ca: CaseLockServerInfoCometActor, _) =>
			infoActors.put(ca.caseIdent, ca)

    case removeAListenerMsg @ RemoveAListener(ca: CaseLockServerInfoCometActor) =>
			infoActors.remove(ca.caseIdent)

    case setInfo: SetInfo =>
      infoActors.get(setInfo.info._1).foreach(_ ! setInfo)
  }
}

class CaseLockServerInfoCometActor extends CometActor with CometListener {
  lazy val caseIdent = name.open_!.split("_")(0).toInt
  protected def registerWith = AdditionalInfoServer

  private var info: String = "Ingen låser"

  override def lowPriority = {
    case setInfo @ SetInfo(i: (Int, String)) =>
      info = i._2
      reRender()
  }

  def render = {
    "#caseIdent" #> ("Sak nr: %d" format caseIdent) &
    "#info *" #> info
  }

}

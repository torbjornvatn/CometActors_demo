package no.bekk.cometactors.snippet

import net.liftweb._
import http.S
import util.Helpers._

class CaseLockServerSnippet {

  def view = {
    val caseIdent = S.attr("caseIdent") openOr ""
     "*" #> {
          <div id="lockServerContainer" class={"lift:comet?type=CaseLockServerInfoCometActor;name="+caseIdent}>
            <q>Case Lock Server</q>
            <p id="caseIdent">0</p>
            <p id="info">Hepp</p>
        </div>
      }
  }

}

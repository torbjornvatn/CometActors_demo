package no.bekk.cometactors.snippet

import net.liftweb._
import http.S
import util.Helpers._

class CaseLockSnippet {

  def view = {
    val caseIdent = S.attr("caseIdent") openOr ""
    val userIdent = S.attr("userIdent") openOr ""

      "*" #> {
          <div id="lockActorContainer" class={"lift:comet?type=CaseLockCometActor;name="+Seq(caseIdent, userIdent).mkString("_")}>
            <p>#{userIdent}</p>
            <span id="lockButton" style="">
              <a href="#" class="">
              </a>
            </span>
            <span id="unlockButton" style="display: none">
              <a href="#">
              </a>
            </span>
            <span id="locked" style="display: none">
            </span>
        </div>
      }
  }
}

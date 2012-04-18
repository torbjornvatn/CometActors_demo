package no.bekk.cometactors.snippet

class CaseLockSnippet {

  def view = {

    <div id="lockActorContainer" class={"lift:comet?type=CaseLockCometActor;name="+
                                        List(1, 1).mkString("_")}>
			<span id="lockButton" style="display: none">
				<a href="#"><img src="Open_Padlock.svg"/></a>
			</span>
			<span id="unlockButton" style="display: none">
				<a href="#"><img src="Padlock-green.svg"/></a>
			</span>
			<span id="locked" style="display: none">
        <img src="Padlock-red.svg"/>
      </span>
		</div>

  }

}

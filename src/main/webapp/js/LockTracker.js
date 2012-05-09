LockTracker = (function() {

    return {
		greenLock: function(cometId) {
            $("#"+cometId).find('#unlockButton').show();
            $("#"+cometId).find('#lockButton').hide();
            $("#"+cometId).find('#locked').hide();
        },
        redLock: function(cometId) {
            $("#"+cometId).find('#unlockButton').hide();
            $("#"+cometId).find('#lockButton').hide();
            $("#"+cometId).find('#locked').show();
        },
        openLock: function(cometId) {
            $("#"+cometId).find('#unlockButton').hide();
            $("#"+cometId).find('#lockButton').show();
            $("#"+cometId).find('#locked').hide();
        }
	};

})();
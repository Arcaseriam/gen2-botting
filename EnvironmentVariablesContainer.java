package stringflow.cea;

import stringflow.rta.gen2.Gen2Game;

public class EnvironmentVariablesContainer {
	boolean isRendering;
	Gen2Game game;
	int initRtc;
	
	public EnvironmentVariablesContainer(
			boolean isRendering,
			Gen2Game game,
			int initRtc) {
		this.isRendering = isRendering;
		this.game = game;
		this.initRtc = initRtc;
	}
}

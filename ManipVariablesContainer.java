package stringflow.cea;

public class ManipVariablesContainer {
	int introBackout;
	int delay;
	int partySize;
	int gender;
	int startMinute;
	int poisonStepCount;
	boolean isBiking;
	String offset;
	int startFrame;
	String path;
	
	public ManipVariablesContainer(
			int introBackout,
			int delay,
			int partySize,
			int gender,
			int startMinute,
			int poisonStepCount,
			boolean isBiking,
			String offset,
			int startFrame,
			String path) {
		this.introBackout = introBackout;
		this.delay = delay;
		this.partySize = partySize;
		this.gender = gender;
		this.startMinute = startMinute;
		this.poisonStepCount = poisonStepCount;
		this.isBiking = isBiking;
		this.offset = offset;
		this.startFrame = startFrame;
		this.path = path;
	}
}

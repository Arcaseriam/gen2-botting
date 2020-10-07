package stringflow.cea;

import java.lang.Runnable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

import stringflow.rta.libgambatte.Gb;
import stringflow.rta.libgambatte.LoadFlags;
import stringflow.rta.util.GSRUtils;

public class IGTFrameTester implements Runnable {
	private int threadID;
	private int numThreads;
	private byte[] introState;

	private List<Thread> threadsList;
	private BlockingQueue<Boolean> queue;
	
	private ManipVariablesContainer vars;
	private TargetVariablesContainer target;
	private EnvironmentVariablesContainer env;
	
	public IGTFrameTester(int threadID, int numThreads,
			List<Thread> threadsList, BlockingQueue<Boolean> queue,
			byte[] introState, 
			ManipVariablesContainer vars, TargetVariablesContainer target, EnvironmentVariablesContainer env) {
		this.threadID = threadID;
		this.numThreads = numThreads;
		
		this.threadsList = threadsList;
		this.queue = queue;
		this.introState = Arrays.copyOf(introState, introState.length);
		
		this.vars = vars;
		this.target = target;
		this.env = env;
	}
	
	/*
	public void setLatch(CountDownLatch latch) {
		this.latch = latch;
	}
	 */
	
	@Override
	public synchronized void run() {
		Gb gb = new Gb();
		System.out.println(threadID+":"+gb);
		if(env.isRendering)
			gb.createRenderContext(1);
		
		gb.loadBios("roms/gbc_bios.bin");
		gb.loadRom("roms/crystal_dvcheck.gbc", env.game, LoadFlags.CGB_MODE | LoadFlags.GBA_FLAG | LoadFlags.READONLY_SAV);
		GSRUtils.writeRTC(introState,env.initRtc);
		
		for(int frame = threadID; frame < 60; frame += numThreads) {
			this.introState[StateAddr.igtSecond] = (byte) 0;
			this.introState[StateAddr.igtFrame] = (byte) frame;
			
			System.out.println("Thread "+threadID+" loads introStrate on frame "+String.format("%2d",frame));
			gb.loadState(introState);
			gb.setInjectInputs(false);
			
			//System.out.print("f"+String.format("%2d",frame));
	        boolean isSuccess = PathChecker.executePathWithEncounterCheck(gb, vars.path, target.pokemonID, target.itemID, env.game);
	        System.out.println("t-"+threadID+ " f"+introState[StateAddr.igtFrame]+" : "+((isSuccess)?"OK":"fail"));
	        //synchronized(queue) {
	        	if(!isSuccess) queue.add(isSuccess);
	        //}
		}
		
		System.out.println("Destroying thread "+threadID);
		//synchronized(threadsList) {
			gb.destroy();
			threadsList.set(threadID, null);
		//}
		
        //latch.countDown();
	}
}

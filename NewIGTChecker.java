package stringflow.cea;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class NewIGTChecker {
	private List<Thread> syncThreadsList;
	private BlockingQueue<Boolean> queue;
	private int totalSuccesses;
	private int successThreshold;
	
	private final int sleepDelay = 5; // milliseconds
	
	
	public NewIGTChecker(int successThreshold) {
		List<Thread> threadsList = new ArrayList<Thread>();
		this.syncThreadsList = Collections.synchronizedList(threadsList);
		this.queue = new LinkedBlockingQueue<Boolean>();
		
		this.totalSuccesses = 60;
		this.successThreshold = successThreshold;
	}
	
	public boolean isAboveThreshold() {
		return (totalSuccesses >= successThreshold);
	}
	
	public int decreaseSuccesses() {
		return --this.totalSuccesses;
	}
	
	public int getSuccesses() {
		return totalSuccesses;
	}
	
	private void exit() {
		synchronized(syncThreadsList) {
			for(int i = 0; i < syncThreadsList.size(); i++) {
				syncThreadsList.set(i, null);
				syncThreadsList.remove(i);
			}
		}
		
		synchronized(queue) {
			queue.clear();
		}
	}
	
	private void sleep() {
		try {
			Thread.sleep(1);
		} catch(InterruptedException e) {}
	}
	
	public synchronized void checkIGTFrames(int numThreads, byte[] introState,
			ManipVariablesContainer vars, TargetVariablesContainer target, EnvironmentVariablesContainer env) {		
		//synchronized(syncThreadsList) {
			// Create all threads with an IGTFrameTester each
			for(int threadID = 0; threadID < numThreads; threadID++) {
				syncThreadsList.add(new Thread(
						new IGTFrameTester(
							threadID, numThreads,
							syncThreadsList, queue,
							introState,
							vars, target, env
						)
				));
			}
			
			System.out.println(syncThreadsList.size()+" threads created");
			
			// Start all threads
			for(Thread t : syncThreadsList) {
				t.start();
			}
			
			System.out.println(syncThreadsList.size()+" threads started");
			
			// Wait for at least one thread to run
			while(syncThreadsList.isEmpty()) {
				try {
					Thread.sleep(sleepDelay);
				} catch(InterruptedException e) {}
			}
			
			System.out.println("Starting to process the queue");
			
			// Process the information sent by all threads into the queue
			while( ! (syncThreadsList.stream().allMatch(x -> x == null) && queue.isEmpty()) ) {
				//System.out.println("ThreadList : "+syncThreadsList.toString());
				//System.out.println("Queue : "+queue.toString());
				try {
					Boolean isSuccess;
					//synchronized(queue) {
						isSuccess = queue.poll(sleepDelay, TimeUnit.MILLISECONDS);
					//}
					
					if(isSuccess != null && !isSuccess) {
						decreaseSuccesses();
						System.out.println("Queue retrieved an unsuccesful IGT frame");
					}
					if(!this.isAboveThreshold()) {
						exit();
						break;
					}
					sleep();
					
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			System.out.println("Cleanup !!!");
			
			// Cleanup
			syncThreadsList.clear();
			
		//}
	}
}

package stringflow.cea;

import java.util.Arrays;

import stringflow.cbt.CrystalAddr;
import stringflow.cbt.GscAction;
import stringflow.rta.Address;
import stringflow.rta.gen2.Gen2Game;
import stringflow.rta.libgambatte.Gb;

public class PathChecker {
	public static boolean executePathWithEncounterCheck(Gb gbIGT, String path, int targetPokemonID, int targetItemID, Gen2Game game) {
		String debugRngStr = "";
    	String[] actions = path.split(" ");
    	actions = Arrays.copyOfRange(actions, 1, actions.length);
    	
    	boolean isSuccess = false;
    	
    	System.out.println(gbIGT);
    	System.out.println("1");
    	
        gbIGT.hold(Inputs.A);
        gbIGT.runUntil(CrystalAddr.readJoypadAddr);
        System.out.println("2");

        gbIGT.frameAdvance();

        gbIGT.runUntil(CrystalAddr.owPlayerInputAddr);
        gbIGT.setInjectInputs(true);
        System.out.println("3");

        //String log = "[" + delay + "] ";
        Address ret = new Address("owPlayerInputAddr", CrystalAddr.owPlayerInputAddr);
        for (String action : actions) {
            //log += action + " ";
            GscAction owAction = GscAction.fromString(action);
            ret = executeAction(gbIGT, ret, owAction, game);

            debugRngStr += "["+gbIGT.getRdiv()+","+gbIGT.getRandomAdd()+","+gbIGT.getRandomSub()+"]-> ";
        }
        if(ret.getAddress() != CrystalAddr.calcStatsAddr) {
            //System.out.println("[" + f + "] NO ENCOUNTER" );
        } else {
        	int pokemonID = gbIGT.read(0xD206);
        	int itemID = gbIGT.read(0xD207);
        	//System.out.println(pokemonID+"->"+targetPokemonID+"/"+itemID+"->"+targetItemID);
        	if(pokemonID == targetPokemonID
        	   && (itemID == -1 || itemID > -1 && itemID == targetItemID))
        		isSuccess = true;
        }
        
        System.out.println(debugRngStr+isSuccess);
        return isSuccess;
    }
	
	 private static Address executeAction(Gb gbIGT, Address ret, GscAction action, Gen2Game game) {
	        if(ret.getAddress() != CrystalAddr.owPlayerInputAddr) {
	            return ret;
	        }
	        if(GscAction.isDpad(action)) {
	            int input;
	            if(action.logStr().startsWith("A")) {
	                input = 1 | (16 * (int) (Math.pow(2.0, (action.ordinal()-8))));
	            }
	            else {
	                input = 16 * (int) (Math.pow(2.0, (action.ordinal())));
	            }
	            gbIGT.hold(input);
	            ret = gbIGT.runUntil(CrystalAddr.countStepAddr, CrystalAddr.calcStatsAddr,
	                    CrystalAddr.printLetterDelayAddr, CrystalAddr.bonkSoundAddr);

	            if(ret.getAddress() == CrystalAddr.countStepAddr) {
	            	gbIGT.hold(input);
	                ret = gbIGT.runUntil(CrystalAddr.owPlayerInputAddr, CrystalAddr.calcStatsAddr);
	            }
	        }
	        else if(action == GscAction.SEL) {
	        	gbIGT.hold(Inputs.SELECT);
	        	gbIGT.frameAdvance();
	            ret = gbIGT.runUntil(CrystalAddr.owPlayerInputAddr);
	        }
	        else if(action == GscAction.START_B) {
	        	gbIGT.hold(Inputs.START);
	        	gbIGT.frameAdvance();
	        	gbIGT.runUntil(CrystalAddr.readJoypadAddr);
	        	gbIGT.hold(Inputs.B, game.getMenuInjection());
	        	gbIGT.frameAdvance();
	            ret = gbIGT.runUntil(CrystalAddr.owPlayerInputAddr);
	        }
	        return ret;
	    }
}

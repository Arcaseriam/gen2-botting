package stringflow.cea;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;

import stringflow.cbt.*;
import stringflow.cea.EnvironmentVariablesContainer;
import stringflow.cea.ManipVariablesContainer;
import stringflow.cea.TargetVariablesContainer;
import stringflow.rta.Address;
import stringflow.rta.gen2.Gen2Game;
import stringflow.rta.gen2.PokeCrystal;
import stringflow.rta.libgambatte.Gb;
import stringflow.rta.libgambatte.LoadFlags;
import stringflow.rta.util.GSRUtils;
import stringflow.rta.util.IO;

public class ManipCore {
	public static final Gen2Game game = new PokeCrystal();

    private static Gb gb;
    private static PrintWriter writer;
    private static GscTileMap map = new GscTileMap();
    private static HashSet<GscState> seenStates = new HashSet<>();
    private static byte[] introState;    
    
    /* ** CUSTOM VARIABLES ** */
    private static final String savefileString = "cea_ditto_StartMinute0.sav"; // "cbt_cow_test.sav"; // 
    private static final int maxIntroDelay = 240;
    private static final double maxIntroTime = 15.75f;
    
    private static final double manipTimeThreshold = 18.25f;
    private static final int manipStepThreshold = 3;
    
    private static final int igtSuccessThreshold = 55;
    private static final int clusterSize = 1;
    
    private static final int initRtc = (23)*3600 + 55*60 + 0*1; // we just manually set StartHMS to 00:00:00 in the save for convenience here
    private static final boolean isRendering = true;
    
    private static final int pokemonIDToSearchFor = InGameData.dittoID;
    private static final int itemIDToSearchFor = 0; // 0 is "no item"
    private static final int mapID = InGameData.route35_ID;
    
    private static final int numThreads = 3;
    
    /* ** */


    private static void overworldSearch(GscState state, PrintWriter writer, 
    		int introBackout, int delay, int partySize, int gender, int startMinute, int poisonStepCount, boolean isBiking, 
    		String offset, int startFrame){
        //System.out.println(state.getLogStr() + " - " + gb.getGbpTime(initRtc));

        if(!seenStates.add(state)) {
            return;
        }

        // figure out the earliest time we can get an encounter (depends on cooldown)
        int cooldown = gb.read(0xD452);
        double gbpTime = gb.getGbpTime(initRtc);
        double adjGbpTime = (cooldown==0) ? gbpTime : gbpTime + 16.0*((double)(cooldown-1))/59.7275;

        // subtract 62 frames if we saved on the bike (getting off bike wastes the 62 frames pre-save)
        if(state.onBike()) {
            adjGbpTime -= 1.038;
        }

        // cut off if earliest possible encounter is too late or if stepcount exceeds the desired amount
        if((adjGbpTime > manipTimeThreshold || gb.read(0xDC73) > manipStepThreshold)) {
            return;
        }

        byte[] oldState = gb.saveState();
        GscTile tile = map.get(mapID, state.getX(), state.getY());
        
        for(GscEdge edge : tile.getEdges().get(0)) {
            //System.out.println("["+state.getX() + ";" + state.getY() + "]->" + edge.toString());
            
            if(GscAction.isDpad(edge.getAction())) {
                int input = 16 * (int) (Math.pow(2.0, (edge.getAction().ordinal())));
                
                gb.hold(input);
                //Address ret = gb.runUntil(CrystalAddr.countStepAddr, CrystalAddr.startWildBattleAddr);
                Address ret = gb.runUntil(CrystalAddr.countStepAddr, CrystalAddr.startWildBattleAddr, CrystalAddr.printLetterDelayAddr, CrystalAddr.bonkSoundAddr);
                //boolean turnframeEnc = true;
                if(ret.getAddress() == CrystalAddr.bonkSoundAddr) {
                	/* The new automated grid system generation doesn't discard
                	 * the impossible player movements. Checking for a bonk
                	 * means the manip can't be easily reproduced. 
                	 */
                	//System.out.println("BONK");
                	gb.loadState(oldState);
                	continue;
                }
                if(ret.getAddress() == CrystalAddr.printLetterDelayAddr) {
                	/* Here this can mean we're hitting a trainer or something,
                	 * so we discard this search.
                	 */
                	//System.out.println("PRINTLETTERDELAY");
                	gb.loadState(oldState);
                	continue;
                }
                if(ret.getAddress() == CrystalAddr.countStepAddr) {
                	gb.hold(input);
                    ret = gb.runUntil(CrystalAddr.owPlayerInputAddr, CrystalAddr.startWildBattleAddr);
                    //turnframeEnc = false;
                }
                if(ret.getAddress() == CrystalAddr.startWildBattleAddr) {
                    gb.runUntil(CrystalAddr.calcStatsAddr);
                    if(gb.read(0xD206) == pokemonIDToSearchFor) {
                        //String turnframeStr = turnframeEnc ? " [turnframe]" : "";
                        state.appendToLog(" " + edge.getAction().logStr());
                        System.out.println(state.getLogStr());
                        
                        ManipVariablesContainer vars = new ManipVariablesContainer(
                        		introBackout,
                    			delay,
                    			partySize,
                    			gender,
                    			startMinute,
                    			poisonStepCount,
                    			isBiking,
                    			offset,
                    			startFrame,
                    			state.getLogStr()
                        		);
                        TargetVariablesContainer target = new TargetVariablesContainer(pokemonIDToSearchFor, itemIDToSearchFor);
                        EnvironmentVariablesContainer env = new EnvironmentVariablesContainer(isRendering, game, initRtc);
                        NewIGTChecker igtChecker = new NewIGTChecker(igtSuccessThreshold);
                        
                        igtChecker.checkIGTFrames(numThreads, introState, vars, target, env);
                        System.out.println("After checker : success="+igtChecker.getSuccesses());
                        System.exit(0);
                        /*
                        writer.println(state.getLogStr() + " " + edge.getAction().logStr() + turnframeStr);
                        writer.print("    - DVs = " + getDVs()
                                + " (" + gb.read(0xD219)
                                + "/" + gb.read(0xD21B)
                                + "/" + gb.read(0xD21D)
                                + "/" + gb.read(0xD221)
                                + "/" + gb.read(0xD223)
                                + "/" + gb.read(0xD21F) + ")"
                        );
                        writer.println("  --  Item: " + gb.read(0xD207) + "  --  calcStats: " + gb.getEonTimer(initRtc));
                        writer.flush();
                        */
                        
                        //gb.loadState(oldState);
                        
                        // Let's start the igtChecker
                        /*try {
							igtCheckSpeciesAndItem(
									introBackout, delay, state.getLogStr(), 
									partySize, gender, startMinute, poisonStepCount, isBiking, offset, startFrame, 
									pokemonIDToSearchFor, itemIDToSearchFor, igtSuccessThreshold, clusterSize);
									*/
							
							gb.loadState(oldState);
							return; // we're done with this movement, so we return
						/*} catch (IOException e) 
							e.printStackTrace();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}*/
                        
                       // gb.loadState(oldState);
                    }
                }
                else {
                	//System.out.println("["+gb.getRdiv()+","+gb.read(0xFFE1)+","+gb.read(0xFFE2)+"]");
                	
                    GscState newState = new GscState(
                            state.getLogStr() + " " + edge.getAction().logStr(),
                            state.onBike(),
                            (state.getFacingDir() != input) || state.turnframeStatus(),
                            state.justChangedDir(),
                            input,
                            gb.getRdiv(),
                            gb.read(0xFFE1),
                            gb.read(0xFFE2),
                            edge.getNextTile().getX(),
                            edge.getNextTile().getY(),
                            true,
                            true,
                            false
                    );
                    overworldSearch(newState, writer, 
                    		introBackout, delay, partySize, gender, startMinute, poisonStepCount, isBiking, 
                    		offset, startFrame);
                }
            }
            else if(edge.getAction() == GscAction.START_B) {
                if(!state.canStart()) {
                    continue;
                }
                gb.hold(Inputs.START);
                gb.frameAdvance();
                gb.runUntil(CrystalAddr.readJoypadAddr);
                gb.hold(Inputs.B, game.getMenuInjection());
				gb.frameAdvance();
                gb.runUntil(CrystalAddr.owPlayerInputAddr);
                GscState newState = new GscState(
                        state.getLogStr() + " " + edge.getAction().logStr(),
                        state.onBike(),
                        true,
                        false,
                        state.getFacingDir(),
                        gb.getRdiv(),
                        gb.read(0xFFE1),
                        gb.read(0xFFE2),
                        edge.getNextTile().getX(),
                        edge.getNextTile().getY(),
                        true,
                        true,
                        true
                );
                overworldSearch(newState, writer, 
                		introBackout, delay, partySize, gender, startMinute, poisonStepCount, isBiking, 
                		offset, startFrame);
            }
            else if(edge.getAction() == GscAction.SEL) {
                if(!state.canSelect()) {
                    continue;
                }
                gb.hold(Inputs.SELECT);
                gb.frameAdvance();
                Address ret = gb.runUntil(CrystalAddr.owPlayerInputAddr);
                GscState newState = new GscState(
                        state.getLogStr() + " " + edge.getAction().logStr(),
                        !state.onBike(), // not sure here TO-DO
                        true,
                        false,
                        state.getFacingDir(),
                        gb.getRdiv(),
                        gb.read(0xFFE1),
                        gb.read(0xFFE2),
                        edge.getNextTile().getX(),
                        edge.getNextTile().getY(),
                        false,
                        true,
                        true
                );
                overworldSearch(newState, writer, 
                		introBackout, delay, partySize, gender, startMinute, poisonStepCount, isBiking, 
                		offset, startFrame);
                
                //String logStr = state.getLogStr() + " SEL";
                /* hold down after getting on bike until encounter or out of grass (simple enough increase in search space)
                while(gb.read(0xDCB7) < 27 && ret.getAddress() == CrystalAddr.owPlayerInputAddr) {
                    int input = DOWN;
                    gb.hold(input);
                    ret = gb.runUntil(CrystalAddr.countStepAddr, CrystalAddr.startWildBattleAddr);
                    logStr += " D";
                    boolean turnframeEnc = true;
                    if (ret.getAddress() == CrystalAddr.countStepAddr) {
                    	gb.hold(input);
                        ret = gb.runUntil(CrystalAddr.owPlayerInputAddr, CrystalAddr.startWildBattleAddr);
                        turnframeEnc = false;
                    }
                    if (ret.getAddress() == CrystalAddr.startWildBattleAddr) {
                        gb.runUntil(CrystalAddr.calcStatsAddr);
                        if (gb.read(0xD206) == pokemonIDToSearchFor) {
                            String turnframeStr = turnframeEnc ? " [turnframe]" : "";
                            writer.println(logStr + turnframeStr);
                            writer.print("    - DVs = " + getDVs()
                                    + " (" + gb.read(0xD219)
                                    + "/" + gb.read(0xD21B)
                                    + "/" + gb.read(0xD21D)
                                    + "/" + gb.read(0xD221)
                                    + "/" + gb.read(0xD223)
                                    + "/" + gb.read(0xD21F) + ")"
                            );
                            writer.println("  --  Item: " + gb.read(0xD207) + "  --  calcStats: " + gb.getEonTimer(initRtc));
                            writer.flush();
                        }
                    }
                }
                */
            }
            gb.loadState(oldState);
        }
    }

    private static void search() throws IOException {
        initGrid();
        String ts = Long.toString(System.currentTimeMillis());
        String fileName = savefileString + "_" + ts + ".txt";
        System.out.println(fileName);
        writer = new PrintWriter(fileName);
        //writer = new PrintWriter(System.out);
        
        int maxIntroDelay = ManipCore.maxIntroDelay;
        double maxIntroTime = ManipCore.maxIntroTime;

        int[] partySizes = {6};
        for (int partySize : partySizes) {
            //for (int gender = 0; gender <= 1; gender++) {
        	int gender = 1; // female
                //for (int sm = 59; sm >= 0; sm -= 59) {
        	int sm = 0;
                    //for (int psc = 0; psc < 4; psc += 2) {
        	for (int psc = 0; psc < 2; psc++) {
                        for (int bike = 0; bike <= 1; bike++) {
                            makeSave(partySize, gender, sm, psc, (bike == 1), 0);
                    		gb = new Gb();
                    		gb.loadBios("roms/gbc_bios.bin");
                    		gb.loadRom("roms/crystal_dvcheck.gbc", game, LoadFlags.CGB_MODE | LoadFlags.GBA_FLAG | LoadFlags.READONLY_SAV);
                    		byte saveState[] = gb.saveState();
                    		GSRUtils.writeRTC(saveState,initRtc);
                    		gb.loadState(saveState);

                    		if(isRendering)
                    			gb.createRenderContext(1);
                    		gb.runUntil(0x100);
                    		gb.setInjectInputs(false);

                    		gb.hold(Inputs.START);
                			gb.runUntil(CrystalAddr.readJoypadAddr);
                			gb.frameAdvance();
                			
                			gb.runUntil(CrystalAddr.readJoypadAddr);
                			gb.frameAdvance();
           
                            int back = 0;
                            byte[] backoutState = gb.saveState();
                            for (; back <= 2; back++) {
                    			gb.hold(Inputs.A);
                    			gb.runUntil(CrystalAddr.readJoypadAddr);
                    			gb.frameAdvance();

                    			gb.hold(0);
                    			gb.runUntil(CrystalAddr.readJoypadAddr);
                    			gb.frameAdvance();

                                introState = gb.saveState();
                                for (int d = 0; d <= maxIntroDelay && gb.getGbpTime(initRtc) < maxIntroTime; d++) {
                                	System.out.println("Delay : "+d);
                                    String offset = gb.getEonTimer(initRtc);
                                    gb.hold(Inputs.A);
                                    gb.runUntil(CrystalAddr.readJoypadAddr);
                                    gb.frameAdvance();

                                    gb.runUntil(CrystalAddr.owPlayerInputAddr);
                                    gb.setInjectInputs(true);
                                    //String log = "partysize(" + partySize + "), gender(" + gender + "), startmin(" + sm + "), psc(" + psc + "), bike(" + bike + "), backout(" + back + "), delay(" + d + ")";
                                    //log += "  (offset: " + offset + ", frame: " + gb.read(0xFFF0) + ")\n    - Path:";
                                    String log = "";
                                    int startFrame = gb.read(0xFFF0);
                                    
                                    // if we start out on the bike, force the manip to start with unbiking
                                    if (bike == 1) {
                                        gb.hold(Inputs.SELECT);
	                                    gb.frameAdvance();
                                        gb.runUntil(CrystalAddr.owPlayerInputAddr);
                                        log += " SEL";
                                    }

                                    GscState owState = new GscState(
                                            log,
                                            (bike == 1),
                                            false,
                                            false,
                                            Inputs.UP,
                                            gb.getRdiv(),
                                            gb.read(0xFFE1),
                                            gb.read(0xFFE2),
                                            gb.read(0xDCB8),
                                            gb.read(0xDCB7),
                                            true, //false
                                            true,
                                            true);
                                    overworldSearch(owState, writer, 
                                    		back, d, partySize, gender, sm, psc, bike==1, 
                                    		offset, startFrame);
                                    
                                    System.out.println("Next delay soon ...");
                                    
                                    seenStates.clear();
                                    gb.loadState(introState);
                                    gb.setInjectInputs(false);
                                    gb.frameAdvance();
                                    introState = gb.saveState();
                                }
                                gb.loadState(backoutState);
                                gb.setInjectInputs(false);
                                gb.hold(Inputs.B);
                                gb.runUntil(CrystalAddr.readJoypadAddr);
                                gb.frameAdvance();
                                gb.hold(Inputs.START);
                                gb.runUntil(CrystalAddr.readJoypadAddr);
                                gb.frameAdvance();
                                backoutState = gb.saveState();
                            }
                        }
                    }
                //}
            //}
        }
        writer.close();
        gb.destroy();
    }

    public static void makeSave(int partySize, int gender, int startmin, int psc, boolean biking, int frame) throws IOException {
        byte[] baseSave = IO.readBin("baseSaves/"+savefileString);

        baseSave[0x3E3D] = (byte) gender;
        baseSave[0x206A] = (byte) gender;

        baseSave[0x2055] = (byte) 0;
        baseSave[0x2056] = (byte) frame; // igt frame

        baseSave[0x2801] = (byte) 0;     // stepcount
        baseSave[0x2802] = (byte) psc;

        //int startHour = (startmin == 0) ? 17 : 16;
        baseSave[0x2045] = (byte) 0; //startHour; // StartHour
        baseSave[0x2046] = (byte) 0; //startmin; // StartMinute
        baseSave[0x2047] = (byte) 0; // StartSecond

        baseSave[0x24EB] = (byte) (biking ? 1 : 0);

        //if(partySize == 3) {
        //    baseSave[0x2865] = (byte) 3;
        //    baseSave[0x2869] = (byte) 0xFF;
        //}

        int csum1 = 0;
        for (int i = 0x2009; i <= 0x2B82; i++) {
            csum1 += baseSave[i] & 0xFF;
        }
        csum1 = (csum1 & 0xFFFF) ^ 0xFFFF;
        baseSave[0x2D0E] = (byte) ((csum1/256 & 0xFF) ^ 0xFF);
        baseSave[0x2D0D] = (byte) ((csum1%256 & 0xFF) ^ 0xFF);

        int csum2 = 0;
        for (int j = 0x1209; j <= 0x1D82; j++) {
            csum2 += baseSave[j] & 0xFF;
        }
        csum2 = (csum2 & 0xFFFF) ^ 0xFFFF;
        baseSave[0x1F0E] = (byte) ((csum2/256 & 0xFF) ^ 0xFF);
        baseSave[0x1F0D] = (byte) ((csum2%256 & 0xFF) ^ 0xFF);
        IO.writeBin("roms/crystal_dvcheck.sav", baseSave);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        //igtCheck(85, "SEL L R S_B S_B L R SEL D");
        search();
    }

    
    private static void initGrid() {
    	try {
			byte[] saveArray = Files.readAllBytes(new File("baseSaves/"+savefileString).toPath());

			int saveXCoord = saveArray[0x2846];
			int saveYCoord = saveArray[0x2845];
			//int playerFacing = saveArray[0x2071];
			//System.out.println(saveXCoord+";"+saveYCoord+";"+playerFacing);
			
			int tmpXCoord, tmpYCoord;
			
			// Initializes the tiles of the grid in a diamond shape based on the maximum number of steps allowed
			for(int x = -manipStepThreshold; x <= manipStepThreshold; x++) {
				int yLimit = manipStepThreshold - Math.abs(x);
				for(int y = -yLimit; y <= yLimit; y++) {
					tmpXCoord = saveXCoord + x;
					tmpYCoord = saveYCoord + y;
					
					GscCoord c = new GscCoord(mapID, tmpXCoord, tmpYCoord);
                    GscTile tile = new GscTile(c);
                    map.put(c, tile);
				}
			}
			
			// Creates the links between the tiles defined above
			for(int x = -manipStepThreshold; x <= manipStepThreshold; x++) {
				int yLimit = manipStepThreshold - Math.abs(x);
				for(int y = -yLimit; y <= yLimit; y++) {
					int distToStart = Math.abs(x) + Math.abs(y);
					tmpXCoord = saveXCoord + x;
					tmpYCoord = saveYCoord + y;
					
					GscTile tile = 	   map.get(mapID, tmpXCoord, 	tmpYCoord	 );
		            GscTile tileDown = map.get(mapID, tmpXCoord, 	tmpYCoord + 1);
		            GscTile tileLeft = map.get(mapID, tmpXCoord - 1,tmpYCoord	 );
		            GscTile tileUp =   map.get(mapID, tmpXCoord, 	tmpYCoord - 1);
		            GscTile tileRight =map.get(mapID, tmpXCoord + 1,tmpYCoord	 );

		            //System.out.print(x+";"+y+"{");
		            if(x < 0 || distToStart != manipStepThreshold) {
		            	tile.addEdge(0, new GscEdge(GscAction.RIGHT, tileRight));
		            	//System.out.print("RIGHT,");
		            }
		            if(x > 0 || distToStart != manipStepThreshold) {
		            	tile.addEdge(0, new GscEdge(GscAction.LEFT, tileLeft));
		            	//System.out.print("LEFT,");
		            }
		            if(y < 0 || distToStart != manipStepThreshold) {
		            	tile.addEdge(0, new GscEdge(GscAction.DOWN, tileDown));
		            	//System.out.print("DOWN,");
		            }
		            if(y > 0 || distToStart != manipStepThreshold) {
		            	tile.addEdge(0, new GscEdge(GscAction.UP, tileUp));
		            	//System.out.print("LEFT,");
		            }
		            //System.out.println("START_B, SELECT}");
		            tile.addEdge(0, new GscEdge(GscAction.START_B, tile));
                    tile.addEdge(0, new GscEdge(GscAction.SEL, tile));
				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    /*
    private static String getDVs() {
        int dvs = (gb.read(0xD20C) << 8) | gb.read(0xD20D); 
        return String.format("0x%4s", Integer.toHexString(dvs).toUpperCase()).replace(' ', '0');
    }
    */
}

/*  Going-through-textboxes routine (TO-DO: needs tests)
		boolean cont = true;

        while (cont) {
        	gb.runUntil(CrystalAddr.readJoypadAddr);
        	int sp = gb.getRegister(Gb.Register.SP);
        	Address ret = gb.runUntil(sp);
        	if(ret.getAddress() == CrystalAddr.printLetterDelayAddr) {
        	    gb.frameAdvance();
        	} else {
        	    cont = false;
        	}
        }
 */
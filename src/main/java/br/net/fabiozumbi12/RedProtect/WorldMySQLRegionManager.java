package br.net.fabiozumbi12.RedProtect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandException;
import org.bukkit.entity.Player;

import br.net.fabiozumbi12.RedProtect.config.RPConfig;

@SuppressWarnings("deprecation")
class WorldMySQLRegionManager implements WorldRegionManager{

	private String url = "jdbc:mysql://"+RPConfig.getString("mysql.host")+"/";
	private String reconnect = "?autoReconnect=true";
	private String dbname = RPConfig.getString("mysql.db-name");
	private boolean tblexists = false;
	private String tableName;
    private Connection dbcon;

    private HashMap<String, Region> regions;
    private World world;
    
    public WorldMySQLRegionManager(World world) throws SQLException{
        super();
        this.regions = new HashMap<String, Region>();
        this.world = world;
        this.tableName = RPConfig.getString("mysql.table-prefix")+world.getName();
        
        this.dbcon = null;
        try {
            Class.forName("com.mysql.jdbc.Driver");
        }
        catch (ClassNotFoundException e2) {
            RedProtect.logger.severe("Couldn't find the driver for MySQL! com.mysql.jdbc.Driver.");
            RedProtect.plugin.disable();
            return;
        }
        PreparedStatement st = null;
        try {
            if (!this.checkTableExists()) {    
            	Connection con = DriverManager.getConnection(this.url + this.dbname + this.reconnect, RPConfig.getString("mysql.user-name"), RPConfig.getString("mysql.user-pass"));
                
                st = con.prepareStatement("CREATE TABLE " + tableName + " (name varchar(20) PRIMARY KEY NOT NULL, leaders longtext, admins longtext, members longtext, maxMbrX int, minMbrX int, maxMbrZ int, minMbrZ int, centerX int, centerZ int, minY int, maxY int, date varchar(10), wel longtext, prior int, world varchar(16), value Long not null, tppoint mediumtext, rent longtext, flags longtext) CHARACTER SET utf8 COLLATE utf8_general_ci");
                st.executeUpdate();
                st.close();
                st = null;
                RedProtect.logger.info("Created table: "+tableName+"!");  
                
            }
            ConnectDB();
            addNewColumns();
        }
        catch (CommandException e3) {
            RedProtect.logger.severe("Couldn't connect to mysql! Make sure you have mysql turned on and installed properly, and the service is started. Reload the Redprotect plugin after you fix or change your DB configurations");
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        finally {
            if (st != null) {
            	st.close();					
            }
        }
    }
    
	private boolean checkTableExists() {
        if (this.tblexists) {            
            return true;
        }     
        try {   
        	RedProtect.logger.debug("Checking if table exists... " + tableName);
        	Connection con = DriverManager.getConnection(this.url + this.dbname, RPConfig.getString("mysql.user-name"), RPConfig.getString("mysql.user-pass"));
            DatabaseMetaData meta = con.getMetaData();
            ResultSet rs = meta.getTables(null, null, tableName, null);
            if (rs.next()) {
            	con.close();
            	rs.close();
            	return true;               
            }    
            con.close();
        	rs.close();
        } catch (SQLException e){
        	e.printStackTrace();
        }        
        return false;
    }
    
	private void addNewColumns(){}
	
	
	/*-------------------------------------- Live Actions -------------------------------------------*/	
    @Override
    public void remove(Region r) {
    	removeLiveRegion(r);
        if (this.regions.containsKey(r.getName())){
        	this.regions.remove(r.getName());
        }
    }   
    private void removeLiveRegion(Region r) {
        if (this.regionExists(r.getName())) {
            try {
                PreparedStatement st = this.dbcon.prepareStatement("DELETE FROM "+tableName+" WHERE name = '" + r.getName() + "'");
                st.executeUpdate();
                st.close();                
            }
            catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void add(Region r) {
		addLiveRegion(r);       
    }
	
    private void addLiveRegion(Region r){
    	if (!this.regionExists(r.getName())) {
            try {                
                PreparedStatement st = null;                
                st = this.dbcon.prepareStatement("INSERT INTO "+tableName+" (name,leaders,admins,members,maxMbrX,minMbrX,maxMbrZ,minMbrZ,minY,maxY,centerX,centerZ,date,wel,prior,world,value,tppoint,rent,flags) VALUES "
                		+ "('" +r.getName() + "', '" + 
                		r.getLeaders().toString().replace("[", "").replace("]", "") + "', '" + 
                		r.getAdmins().toString().replace("[", "").replace("]", "") + "', '" + 
                		r.getMembers().toString().replace("[", "").replace("]", "") + "', '" + 
                		r.getMaxMbrX() + "', '" + 
                		r.getMinMbrX() + "', '" + 
                		r.getMaxMbrZ() + "', '" + 
                		r.getMinMbrZ() + "', '" + 
                		r.getMinY() + "', '" +
                		r.getMaxY() + "', '" +
                		r.getCenterX() + "', '" + 
                		r.getCenterZ() + "', '" + 
                		r.getDate() + "', '" +
                		r.getWelcome() + "', '" + 
                		r.getPrior() + "', '" + 
                		r.getWorld() + "', '" + 
                		r.getValue()+"', '" +
                		r.getTPPointString()+"', '" +
                		r.getRentString()+"', '" +
                		r.getFlagStrings()+"')");    
                st.executeUpdate();
                st.close();
            }
            catch (SQLException e) {
                e.printStackTrace();
            }
        } 
    }
    
    @Override
    public void removeLiveFlags(String rname, String flag){
    	try {
    		PreparedStatement st = this.dbcon.prepareStatement("SELECT flags FROM "+tableName+" WHERE name='"+rname+"' AND world='"+this.world.getName()+"'");
            ResultSet rs = st.executeQuery();   
            if (rs.next()){
            	String flags = rs.getString("flags");
                String flagsStrings = flags;
                for (String flago:flags.split(",")){
                	String key = flago.split(":")[0];
                	if (key.equals(flag)){
                		flagsStrings = flagsStrings.replace(flago, "").replace(",,", ",");
                		st = this.dbcon.prepareStatement("UPDATE "+tableName+" SET flags='"+flagsStrings+"' WHERE name='" + rname + "'");
                        st.executeUpdate();                    
                        break;
                	}
                }
            }            
            st.close();
            rs.close();
        } 
    	catch (SQLException e) {
        	RedProtect.logger.severe("RedProtect can't save flag for region " + rname + ", please verify the Mysql Connection and table structures.");
            e.printStackTrace();
        } 
    }
    
    @Override
    public void updateLiveRegion(String rname, String columm, String value){
    	try {
            PreparedStatement st = this.dbcon.prepareStatement("UPDATE "+tableName+" SET "+columm+"='"+value+"' WHERE name='" + rname + "'");
            st.executeUpdate();
            st.close();
        }
        catch (SQLException e) {
        	RedProtect.logger.severe("RedProtect can't save the region " + rname + ", please verify the Mysql Connection and table structures.");
            e.printStackTrace();
        } 
    }
    
    @Override
    public void updateLiveFlags(String rname, String flag, String value){
    	try {
    		PreparedStatement st = this.dbcon.prepareStatement("SELECT flags FROM "+tableName+" WHERE name='"+rname+"' AND world='"+this.world.getName()+"'");
            ResultSet rs = st.executeQuery();   
            if (rs.next()){
            	String flags = rs.getString("flags");
                String flagsStrings = flags;
                for (String flago:flags.split(",")){
                	String key = flago.split(":")[0];
                	if (key.equals(flag)){
                		flagsStrings = flagsStrings.replace(flago, key+":"+value);
                		st = this.dbcon.prepareStatement("UPDATE "+tableName+" SET flags='"+flagsStrings+"' WHERE name='" + rname + "'");
                        st.executeUpdate();                    
                        break;
                	}
                }
            }            
            st.close();
            rs.close();
        }
        catch (SQLException e) {
        	RedProtect.logger.severe("RedProtect can't save flag for region " + rname + ", please verify the Mysql Connection and table structures.");
            e.printStackTrace();
        }       
    }
    
    @Override
    public void load() {
    	if (this.dbcon == null){
    		ConnectDB();
    	}
    	try {
            PreparedStatement st = this.dbcon.prepareStatement("SELECT * FROM "+tableName+" WHERE world='"+this.world.getName()+"'");
            ResultSet rs = st.executeQuery();            
            while (rs.next()){ 
            	RedProtect.logger.debug("Load Region: "+rs.getString("name")+", World: "+this.world.getName());
            	List<String> leaders = new ArrayList<String>();
            	List<String> admins = new ArrayList<String>();
                List<String> members = new ArrayList<String>();
                HashMap<String, Object> flags = new HashMap<String, Object>();                      
                
                int maxMbrX = rs.getInt("maxMbrX");
                int minMbrX = rs.getInt("minMbrX");
                int maxMbrZ = rs.getInt("maxMbrZ");
                int minMbrZ = rs.getInt("minMbrZ");
                int maxY = rs.getInt("maxY");
                int minY = rs.getInt("minY");
                int prior = rs.getInt("prior");
                String rname = rs.getString("name");
                String world = rs.getString("world");
                String date = rs.getString("date");
                String wel = rs.getString("wel");
                long value = rs.getLong("value");
                
                Location tppoint = null;
                if (rs.getString("tppoint") != null && !rs.getString("tppoint").equalsIgnoreCase("")){
                	String tpstring[] = rs.getString("tppoint").split(",");
                    tppoint = new Location(Bukkit.getWorld(world), Double.parseDouble(tpstring[0]), Double.parseDouble(tpstring[1]), Double.parseDouble(tpstring[2]), 
                    		Float.parseFloat(tpstring[3]), Float.parseFloat(tpstring[4]));
                }                    
                                    
                for (String member:rs.getString("members").split(", ")){
                	if (member.length() > 0){
                		members.add(member);
                	}                	
                }
                for (String admin:rs.getString("admins").split(", ")){
                	if (admin.length() > 0){
                		admins.add(admin);
                	}                	
                }
                for (String leader:rs.getString("leaders").split(", ")){
                	if (leader.length() > 0){
                		leaders.add(leader);
                	}                	
                }
                for (String flag:rs.getString("flags").split(",")){                	
                	String key = flag.split(":")[0];
                	String replace = new String(key+":");
                	if (replace.length() <= flag.length()){
                		flags.put(key, RPUtil.parseObject(flag.substring(replace.length())));  
                	}                	              	
                }
                
                regions.put(rname, new Region(rname, admins, members, leaders, maxMbrX, minMbrX, maxMbrZ, minMbrZ, minY, maxY, flags, wel, prior, world, date, value, tppoint));
            } 
            st.close(); 
            rs.close();                           
        }
        catch (SQLException e) {
            e.printStackTrace();
        } 
    }
    
    /*---------------------------------------------------------------------------------*/
    
    @Override
    public Set<Region> getRegions(String uuid) {
    	Set<Region> regionsp = new HashSet<Region>();
    	try {
            PreparedStatement st = this.dbcon.prepareStatement("SELECT name FROM "+tableName+" WHERE leaders LIKE '%"+uuid+"%'");
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
            	regionsp.add(this.getRegion(rs.getString("name")));
            }
            st.close();
            rs.close();
        }
		catch (SQLException e) {
            e.printStackTrace();
        }    	
		return regionsp;
    }
    
    @Override
    public Set<Region> getMemberRegions(String uuid) {
    	Set<Region> regionsp = new HashSet<Region>();
    	try {
            PreparedStatement st = this.dbcon.prepareStatement("SELECT name FROM "+tableName+" WHERE leaders LIKE '%"+uuid+"%' OR admins LIKE '%"+uuid+"%'");
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
            	regionsp.add(this.getRegion(rs.getString("name")));
            }
            st.close();
            rs.close();
        }
		catch (SQLException e) {
            e.printStackTrace();
        }
		return regionsp;
    }
    
    @Override
    public Region getRegion(final String rname){
    	if (this.dbcon == null){
    		ConnectDB();
    	}
    	if (!regions.containsKey(rname)){
    		if (rname == null){
    			return null;
    		}
    		try {
                PreparedStatement st = this.dbcon.prepareStatement("SELECT * FROM "+tableName+" WHERE name='"+rname+"' AND world='"+this.world.getName()+"'");
                ResultSet rs = st.executeQuery();            
                if (rs.next()){ 
                	List<String> leaders = new ArrayList<String>();
                	List<String> admins = new ArrayList<String>();
                    List<String> members = new ArrayList<String>();
                    HashMap<String, Object> flags = new HashMap<String, Object>();                      
                    
                    int maxMbrX = rs.getInt("maxMbrX");
                    int minMbrX = rs.getInt("minMbrX");
                    int maxMbrZ = rs.getInt("maxMbrZ");
                    int minMbrZ = rs.getInt("minMbrZ");
                    int maxY = rs.getInt("maxY");
                    int minY = rs.getInt("minY");
                    int prior = rs.getInt("prior");
                    String world = rs.getString("world");
                    String date = rs.getString("date");
                    String wel = rs.getString("wel");
                    long value = rs.getLong("value");
                    String rent = rs.getString("rent");
                    
                    Location tppoint = null;
                    if (rs.getString("tppoint") != null && !rs.getString("tppoint").equalsIgnoreCase("")){
                    	String tpstring[] = rs.getString("tppoint").split(",");
                        tppoint = new Location(Bukkit.getWorld(world), Double.parseDouble(tpstring[0]), Double.parseDouble(tpstring[1]), Double.parseDouble(tpstring[2]), 
                        		Float.parseFloat(tpstring[3]), Float.parseFloat(tpstring[4]));
                    }                    
                                        
                    for (String member:rs.getString("members").split(", ")){
                    	if (member.length() > 0){
                    		members.add(member);
                    	}                	
                    }
                    for (String admin:rs.getString("admins").split(", ")){
                    	if (admin.length() > 0){
                    		admins.add(admin);
                    	}                	
                    }
                    for (String leader:rs.getString("leaders").split(", ")){
                    	if (leader.length() > 0){
                    		leaders.add(leader);
                    	}                	
                    }
                    for (String flag:rs.getString("flags").split(",")){
                    	String key = flag.split(":")[0];
                    	flags.put(key, RPUtil.parseObject(flag.substring(new String(key+":").length())));
                    }
                    
                    Region reg = new Region(rname, admins, members, leaders, maxMbrX, minMbrX, maxMbrZ, minMbrZ, minY, maxY, flags, wel, prior, world, date, value, tppoint);
                    if (rent.split(":").length >= 3){
                    	reg.setRentString(rent);
                    }
                    regions.put(rname, reg);
                } else {
                	return null;
                }
                st.close(); 
                rs.close();
                RedProtect.logger.debug("Adding region to cache: "+rname);
                Bukkit.getScheduler().runTaskLater(RedProtect.plugin, new Runnable(){
                		@Override
                		public void run(){
                		if (regions.containsKey(rname)){
                			regions.remove(rname);
                			RedProtect.logger.debug("Removed cached region: "+rname);
                		}
                		}                	
                }, (20*60)*RPConfig.getInt("mysql.region-cache-minutes"));                
            }
            catch (SQLException e) {
                e.printStackTrace();
            }
    	} 
    	return regions.get(rname);
    }
    
    @Override
    public int save() {
		return 0;
	}  
            
    @Override
    public int getTotalRegionSize(String uuid) {
		int total = 0;
		for (Region r2 : this.getRegions(uuid)) {
			total += RPUtil.simuleTotalRegionSize(uuid, r2);
        }
		return total;
    }
        
    @Override
    public Set<Region> getRegionsNear(Player player, int radius) {
    	int px = player.getLocation().getBlockX();
        int pz = player.getLocation().getBlockZ();
        Set<Region> ret = new HashSet<Region>();
        
        try {
            PreparedStatement st = this.dbcon.prepareStatement("SELECT name FROM "+tableName+" WHERE ABS(centerX-" + px + ")<=" + radius + " AND ABS(centerZ-" + pz + ")<=" + radius);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                ret.add(this.getRegion(rs.getString("name")));
            }
            st.close();
            rs.close();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        return ret;
    }
    
    private boolean regionExists(String name) {
        int total = 0;
        try {
            PreparedStatement st = this.dbcon.prepareStatement("SELECT COUNT(*) FROM "+tableName+" WHERE name = '" + name + "'");
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                total = rs.getInt("COUNT(*)");
            }
            st.close();
            rs.close();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        return total > 0;
    }
    
    public World getWorld() {
        return this.world;
    }    
        
	@Override
	public Set<Region> getRegions(int x, int y, int z) {
		Set<Region> regionl = new HashSet<Region>();		
		try {
            PreparedStatement st = this.dbcon.prepareStatement("SELECT name FROM "+tableName+" WHERE " + x + "<=maxMbrX AND " + x + ">=minMbrX AND " + z + "<=maxMbrZ AND " + z + ">=minMbrZ AND " + y + "<=maxY AND " + y + ">=minY");
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
            	regionl.add(this.getRegion(rs.getString("name")));
            }
            st.close();
            rs.close();
        }
		catch (SQLException e) {
            e.printStackTrace();
        }
		return regionl;
	}

	@Override
	public Region getTopRegion(int x, int y, int z) {
		Map<Integer,Region> regionlist = new HashMap<Integer,Region>();
		int max = 0;
		
		for (Region r:this.getRegions(x, y, z)){
			if (x <= r.getMaxMbrX() && x >= r.getMinMbrX() && y <= r.getMaxY() && y >= r.getMinY() && z <= r.getMaxMbrZ() && z >= r.getMinMbrZ()){
				if (regionlist.containsKey(r.getPrior())){
					Region reg1 = regionlist.get(r.getPrior());
					int Prior = r.getPrior();
					if (reg1.getArea() >= r.getArea()){
						r.setPrior(Prior+1);
					} else {
						reg1.setPrior(Prior+1);
					}					
				}
				regionlist.put(r.getPrior(), r);
			}
		}
		
		if (regionlist.size() > 0){
			max = Collections.max(regionlist.keySet());
        }
		return regionlist.get(max);
	}
	
	@Override
	public Region getLowRegion(int x, int y, int z) {
		Map<Integer,Region> regionlist = new HashMap<Integer,Region>();
		int min = 0;

		for (Region r:this.getRegions(x, y, z)){
			if (x <= r.getMaxMbrX() && x >= r.getMinMbrX() && y <= r.getMaxY() && y >= r.getMinY() && z <= r.getMaxMbrZ() && z >= r.getMinMbrZ()){
				if (regionlist.containsKey(r.getPrior())){
					Region reg1 = regionlist.get(r.getPrior());
					int Prior = r.getPrior();
					if (reg1.getArea() >= r.getArea()){
						r.setPrior(Prior+1);
					} else {
						reg1.setPrior(Prior+1);
					}					
				}
				regionlist.put(r.getPrior(), r);
			}
	    }
		
		if (regionlist.size() > 0){
			min = Collections.min(regionlist.keySet());
        }
		return regionlist.get(min);
	}
	
	public Map<Integer,Region> getGroupRegion(int x, int y, int z) {
		Map<Integer,Region> regionlist = new HashMap<Integer,Region>();
		
		for (Region r:this.getRegions(x, y, z)){
			if (x <= r.getMaxMbrX() && x >= r.getMinMbrX() && y <= r.getMaxY() && y >= r.getMinY() && z <= r.getMaxMbrZ() && z >= r.getMinMbrZ()){
				if (regionlist.containsKey(r.getPrior())){
					Region reg1 = regionlist.get(r.getPrior());
					int Prior = r.getPrior();
					if (reg1.getArea() >= r.getArea()){
						r.setPrior(Prior+1);
					} else {
						reg1.setPrior(Prior+1);
					}					
				}
				regionlist.put(r.getPrior(), r);
			}
	    }
		return regionlist;
	}
	
	@Override
	public Set<Region> getAllRegions() {		
		Set<Region> allregions = new HashSet<Region>();		
		//allregions.addAll(regions.values());
		return allregions;
	}

	@Override
	public void clearRegions() {
		regions.clear();
		/*
		try {
            PreparedStatement st = this.dbcon.prepareStatement();
            st.executeUpdate("DELETE FROM region_flags WHERE region = '*'");

            st = this.dbcon.prepareStatement();
            st.executeUpdate("DELETE FROM region WHERE name = '*'");
            st.close();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }		*/
	}

	@Override
	public void closeConn() {
		try {
			if (this.dbcon != null && !this.dbcon.isClosed()){
				this.dbcon.close();				
			}
		} catch (SQLException e) {
			RedProtect.logger.severe("No connections to close! Forget this message ;)");
		}
	}
	
    private boolean ConnectDB() {
    	try {
			this.dbcon = DriverManager.getConnection(this.url + this.dbname+ this.reconnect, RPConfig.getString("mysql.user-name"), RPConfig.getString("mysql.user-pass"));
			RedProtect.logger.info("Conected to "+this.tableName+" via Mysql!");
			return true;
		} catch (SQLException e) {			
			e.printStackTrace();
			RedProtect.logger.severe("["+dbname+"] Theres was an error while connecting to Mysql database! RedProtect will try to connect again in 15 seconds. If still not connecting, check the DB configurations and reload.");
			return false;
		}		
	}
	
	@Override
	public int getTotalRegionNum(){
		int total = 0;
		try {
            PreparedStatement st = this.dbcon.prepareStatement("SELECT COUNT(*) FROM "+tableName);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                total = rs.getInt("COUNT(*)");
            }
            st.close();
            rs.close();
        }
        catch (SQLException e) {
        	RedProtect.logger.severe("Error on get total of regions for "+tableName+"!");
            e.printStackTrace();
        }
		return total;
	}

}

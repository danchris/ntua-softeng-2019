package gr.ntua.ece.softeng18b.data;


import gr.ntua.ece.softeng18b.data.model.Price;
import gr.ntua.ece.softeng18b.data.model.PriceResult;
import gr.ntua.ece.softeng18b.data.model.Product;
import gr.ntua.ece.softeng18b.data.model.ProductWithImage;
import gr.ntua.ece.softeng18b.data.model.Shop;
import org.apache.commons.dbcp2.BasicDataSource;
import org.restlet.resource.ResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.math.BigInteger; 
import java.security.MessageDigest; 
import java.security.NoSuchAlgorithmException; 

public class DataAccess {

    private static final Object[] EMPTY_ARGS = new Object[0];

    private static final int MAX_TOTAL_CONNECTIONS = 16;
    private static final int MAX_IDLE_CONNECTIONS = 8;

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    public void setup(String driverClass, String url, String user, String pass) throws SQLException {

        //initialize the data source
        BasicDataSource bds = new BasicDataSource();
        bds.setDriverClassName(driverClass);
        bds.setUrl(url);
        bds.setMaxTotal(MAX_TOTAL_CONNECTIONS);
        bds.setMaxIdle(MAX_IDLE_CONNECTIONS);
        bds.setUsername(user);
        bds.setPassword(pass);
        bds.setValidationQuery("SELECT 1");
        bds.setTestOnBorrow(true);
        bds.setDefaultAutoCommit(true);

        //check that everything works OK
        bds.getConnection().close();

        //initialize the jdbc template utilitiy
        jdbcTemplate = new JdbcTemplate(bds);
    }

    public List<Product> getProducts(Limits limits, long status, String sort) {
    	Long[] params_small = new Long[]{limits.getStart(),(long)limits.getCount()};
    	Long[] params = new Long[] {status,limits.getStart(),(long)limits.getCount() };
    	if(status == -1) return jdbcTemplate.query("select id, name, description, category, withdrawn, tags from products where 1 order by "+sort+" limit ?,?", params_small, new ProductRowMapper());
    	return jdbcTemplate.query("select id, name, description, category, withdrawn, tags from products where 1 and withdrawn =? order by "+sort+" limit ?,?", params, new ProductRowMapper());      
    }
    
    public List<Shop> getShops(Limits limits, long status, String sort) {
    	Long[] params_small = new Long[]{limits.getStart(),(long)limits.getCount()};
    	Long[] params = new Long[]{status,limits.getStart(),(long)limits.getCount() };
    	if(status == -1) return jdbcTemplate.query("select ST_X(location) as x_coordinate, ST_Y(location) as y_coordinate, id, name, address, tags, withdrawn  from shops where 1 order by "+sort+" limit ?,?", params_small, new ShopRowMapper());
    	return jdbcTemplate.query("select ST_X(location) as x_coordinate, ST_Y(location) as y_coordinate, id, name, address, tags, withdrawn  from shops where 1 and withdrawn =? order by "+sort+" limit ?,?", params, new ShopRowMapper());      
    }
    
    public List<PriceResult> getPrices(Limits limits, String where_clause, String sort, Boolean geo, String shopDist, String have_clause) {
    	Long[] params = new Long[]{limits.getStart(),(long)limits.getCount()};
    	//System.out.println("SELECT price, products.name as product_name, product_id, products.tags as product_tags, shop_id, shops.name as shop_name, shops.tags as shop_tags, shops.address as shop_address, dateFrom, dateTo from prices join shops on shop_id = shops.id join products on product_id = products.id where 1 "+ where_clause +"  order by "+sort+" limit ?,?");
    	if(geo)return jdbcTemplate.query("SELECT price, products.name as product_name, product_id, products.tags as product_tags, shop_id, shops.name as shop_name, shops.tags as shop_tags, shops.address as shop_address, dateFrom, dateTo, "+shopDist+"  from prices join shops on shop_id = shops.id join products on product_id = products.id where 1 "+ where_clause + " "+ have_clause +"  order by "+sort+" limit ?,?", params, new PriceResultRowMapper());
    	return jdbcTemplate.query("SELECT price, products.name as product_name, product_id, products.tags as product_tags, shop_id, shops.name as shop_name, shops.tags as shop_tags, shops.address as shop_address, dateFrom, dateTo from prices join shops on shop_id = shops.id join products on product_id = products.id where 1 "+ where_clause +"  order by "+sort+" limit ?,?", params, new PriceResultRowMapper());      
    }

    public Product addProduct(String name, String description, String category, boolean withdrawn, String tags ) {
        //Create the new product record using a prepared statement
        PreparedStatementCreator psc = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement ps = con.prepareStatement(
                        "insert into products(name, description, category, withdrawn, tags) values(?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, name);
                ps.setString(2, description);
                ps.setString(3, category);
                ps.setBoolean(4, withdrawn);
                ps.setString(5, tags);
                return ps;
            }
        };
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int cnt = jdbcTemplate.update(psc, keyHolder);

        if (cnt == 1) {
            //New row has been added
            Product product = new Product(
                keyHolder.getKey().longValue(), //the newly created project id
                name,
                description,
                category,
                withdrawn,
                tags
            );
            return product;

        }
        else {
            throw new RuntimeException("Creation of Product failed");
        }
    }
    
    public Shop addShop(String name, String address, Double lng, Double lat, boolean withdrawn, String tags ) {
        //Create the new product record using a prepared statement
        PreparedStatementCreator psc = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement ps = con.prepareStatement(
                        "insert into shops(name, address, location, tags, withdrawn) values(?, ?, ST_GeomFromText('POINT("+ lng.toString() +" "+ lat.toString() +")',4326), ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, name);
                ps.setString(2, address);
                ps.setString(3, tags);
                ps.setBoolean(4, withdrawn);
                return ps;
            }
        };
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int cnt = jdbcTemplate.update(psc, keyHolder);

        if (cnt == 1) {
            //New row has been added
            Shop shop = new Shop(
                keyHolder.getKey().longValue(), //the newly created project id
                name,
                address,
                lng,
                lat,
                tags,
                withdrawn
            );
            return shop;

        }
        else {
            throw new RuntimeException("Creation of Shop failed");
        }
    }
    
    public Price addPrice(int product_id, int shop_id, Double price, Date dateFrom, Date dateTo) {
        //Create the new product record using a prepared statement
        PreparedStatementCreator psc = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement ps = con.prepareStatement(
                        "insert into prices(product_id, shop_id, price, dateFrom, dateTo) values(?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                );
                ps.setInt(1, product_id);
                ps.setInt(2, shop_id);
                ps.setDouble(3, price);
                ps.setDate(4, dateFrom);
                ps.setDate(5, dateTo);
                return ps;
            }
        };
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int cnt = jdbcTemplate.update(psc, keyHolder);

        if (cnt == 1) {
            //New row has been added
            Price price_r = new Price(
                product_id,
                shop_id,
                price,
                dateFrom,
                dateTo
            );
            return price_r;

        }
        else {
            throw new RuntimeException("Creation of Price failed");
        }
    }
    
    // Update Product: similar to addProduct
    public Product updateProduct(int id, String name, String description, String category, String withdrawn, String tags ) {
        PreparedStatementCreator psc = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement ps = con.prepareStatement(
                        "update products SET name=? , description=?, category=?, withdrawn=?, tags=? WHERE id=?",
                        Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, name);
                ps.setString(2, description);
                ps.setString(3, category);
                ps.setBoolean(4, withdrawn.equals("1"));
                ps.setString(5, tags);
                ps.setString(6, ""+id);
                return ps;
            }
        };
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int cnt = jdbcTemplate.update(psc, keyHolder);

        if (cnt == 1) {
            //A row has been updated
            Product product = new Product(
                id, //the newly created project id
                name,
                description,
                category,
                withdrawn.equals("1"),
                tags
            );
            return product;

        }
        else {
            throw new RuntimeException("Update of Product failed");
        }
    }
    
 // Update Shop: similar to addProduct
    public Shop updateShop(int id, String name, String address, Double lng, Double lat, String withdrawn, String tags ) {
        PreparedStatementCreator psc = new PreparedStatementCreator() {
        	String location = "ST_GeomFromText('POINT("+lng.toString()+" "+lat.toString()+")', 4326)";
            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement ps = con.prepareStatement(
                        "update shops SET name=? , address=?, location="+location+", withdrawn=?, tags=? WHERE id=?",
                        Statement.RETURN_GENERATED_KEYS
                );
                
                ps.setString(1, name);
                ps.setString(2, address);
                //ps.setString(3, location); 
                ps.setBoolean(3, withdrawn.equals("1"));
                ps.setString(4, tags);
                ps.setString(5, ""+id);
                return ps;
            }
        };
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int cnt = jdbcTemplate.update(psc, keyHolder);

        if (cnt == 1) {
            //A row has been updated
            Shop shop = new Shop(
                id, //the newly created project id
                name,
                address,
                lng,
                lat,
                tags,
                withdrawn.equals("1")
            );
            return shop;

        }
        else {
            throw new RuntimeException("Update of Shop failed");
        }
    }
    
    //Patch Product similar to update product
    public Product patchProduct(int id,String update_parameter, String value) {
        PreparedStatementCreator psc = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE products SET "+update_parameter+"=? where id=?",
                        Statement.RETURN_GENERATED_KEYS
                );
                
                //ps.setString(1, "`"+update_parameter);
                if(update_parameter.equals("withdrawn") && value.equals("1")) ps.setBoolean(1, true);
                else if(update_parameter.equals("withdrawn") && value.equals("0")) ps.setBoolean(1,false);
                else ps.setString(1, value);
                ps.setString(2, ""+id);
                return ps;
            }
        };
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int cnt = jdbcTemplate.update(psc, keyHolder);

        if (cnt == 1) {
            //A specific column of a row has been updated
            Optional<Product> product = this.getProduct(id);
            if(product.isPresent()) return product.get();
            throw new RuntimeException("Retrival of patched of Product failed");

        }
        else {
            throw new RuntimeException("Patch of Product failed");
        }
    }
    
  //Patch Shop similar to update product
    public Shop patchShop(int id,String update_parameter, String value) {
        PreparedStatementCreator psc = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                if(!update_parameter.equals("location")) {
                	PreparedStatement ps = con.prepareStatement(
                			"UPDATE shops SET "+update_parameter+"=? where id=?",
                			Statement.RETURN_GENERATED_KEYS
                	);
                
                	//ps.setString(1, "`"+update_parameter);
                	if(update_parameter.equals("withdrawn") && value.equals("1")) ps.setBoolean(1, true);
                	else if(update_parameter.equals("withdrawn") && value.equals("0")) ps.setBoolean(1,false);
                	else ps.setString(1, value);
                	ps.setString(2, ""+id);
                	return ps;
                }
                else {
                	PreparedStatement ps = con.prepareStatement(
                			"UPDATE shops SET "+update_parameter+"="+value+" where id=?",
                			Statement.RETURN_GENERATED_KEYS
                	);
                	ps.setString(1, ""+id);
                	return ps;
                }
                
            }
        };
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int cnt = jdbcTemplate.update(psc, keyHolder);

        if (cnt == 1) {
            //A specific column of a row has been updated
            Optional<Shop> shop = this.getShop(id);
            if(shop.isPresent()) return shop.get();
            throw new RuntimeException("Retrival of patched of Shop failed");

        }
        else {
            throw new RuntimeException("Patch of Shop failed");
        }
    }
    
    public void deleteProduct(int id) {
        PreparedStatementCreator psc = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM products WHERE id=?",
                        Statement.RETURN_GENERATED_KEYS
                );
                
                ps.setString(1, ""+id);
                return ps;
            }
        };
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int cnt = jdbcTemplate.update(psc, keyHolder);

        if(cnt !=1 ) throw new RuntimeException("Deletion of Product failed");
        return;
    }
    
    public void deleteShop(int id) {
        PreparedStatementCreator psc = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM shops WHERE id=?",
                        Statement.RETURN_GENERATED_KEYS
                );
                
                ps.setString(1, ""+id);
                return ps;
            }
        };
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int cnt = jdbcTemplate.update(psc, keyHolder);

        if(cnt !=1 ) throw new RuntimeException("Deletion of Shop failed");
        return;
    }


    public Optional<Product> getProduct(long id) {
        Long[] params = new Long[]{id};
        List<Product> products = jdbcTemplate.query("select id, name, description, category, withdrawn, tags from products where id = ?", params, new ProductRowMapper());
        if (products.size() == 1)  {
            return Optional.of(products.get(0));
        }
        else {
            return Optional.empty();
        }
    }
    
    public Optional<ProductWithImage> getProductWithImage(long id) {
        Long[] params = new Long[]{id};
        List<ProductWithImage> productswithimage = jdbcTemplate.query("select * from products where id = ?", params, new ProductWithImageRowMapper());
        if (productswithimage.size() == 1)  {
            return Optional.of(productswithimage.get(0));
        }
        else {
            return Optional.empty();
        }
    }
    
    public Optional<Shop> getShop(long id) {
        Long[] params = new Long[]{id};
        List<Shop> shops = jdbcTemplate.query("SELECT ST_X(location) as x_coordinate, ST_Y(location) as y_coordinate, id, name, address, tags, withdrawn from shops where id = ?", params, new ShopRowMapper());
        if (shops.size() == 1)  {
            return Optional.of(shops.get(0));
        }
        else {
            return Optional.empty();
        }
    }

    public String addUser(String fullname, String username, String password, String email, int auth) {
    	//Add new user through a prepared statement
    	PreparedStatementCreator psc = new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
                PreparedStatement ps = con.prepareStatement(
                        "insert into users(fullname, username, password , email, authorization, salt) values(?, ?, ?, ?, ?,?)",
                        Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, fullname);
                ps.setString(2, username);
                ps.setString(3, getSHA(password));
                ps.setString(4, email);
                ps.setInt(5, auth);
                ps.setInt(6,ThreadLocalRandom.current().nextInt(1000, 10000000 + 1));
                return ps;
            }
        };
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int cnt = jdbcTemplate.update(psc, keyHolder);

        if (cnt == 1) {
            return "Succesfull creation of new user";

        }
        else {
            throw new RuntimeException("Creation of Price failed");
        }
    }
    
    public Optional<String> getUserApiToken(String username, String password) {
    	password = getSHA(password);
    	String[] params = new String[]{username,password};
        List<String> pswd_salt = jdbcTemplate.query("select password, salt from users where username = ? AND password = ? ", params, new ApiRowMapper());
        if (pswd_salt.size() == 1)  {
            return Optional.of(getSHA(pswd_salt.get(0)));
        }
        else {
            return null;
        }
    }
    
    public Optional<String> getUserApiToken_username_only(String username) {
    	String[] params = new String[]{username};
        List<String> pswd_salt = jdbcTemplate.query("select password, salt from users where username = ? AND authorization > 1", params, new ApiRowMapper());
        if (pswd_salt.size() == 1)  {
            return Optional.of(getSHA(pswd_salt.get(0)));
        }
        else {
            return null;
        }
    }
    
    public Boolean isLogedIn(String user_token) {
    	String username = user_token.substring(64);
    	String api_token = user_token.substring(0,64);
    	//System.out.println(">>>>>>@@@@@@@@@@@@@@@@@@@@@@@@>>>>>>>>>>>>>>>>");
    	//System.out.println(">>>>>>User is: "+username);
    	//System.out.println(">>>>>>API_token  is: "+api_token);
    	
    	Optional<String> optional = getUserApiToken_username_only(username);
    	if(optional == null) return false;
    	String test_token = optional.orElseThrow(() -> new ResourceException(401, "Login failed. Wrong username or password"));
    	
    	if(test_token.equals(api_token)) return true;
    	return false;
    }
    
    public static String getSHA(String input) 
    { 
  
        try { 
  
            // Static getInstance method is called with hashing SHA 
            MessageDigest md = MessageDigest.getInstance("SHA-256"); 
  
            // digest() method called 
            // to calculate message digest of an input 
            // and return array of byte 
            byte[] messageDigest = md.digest(input.getBytes()); 
  
            // Convert byte array into signum representation 
            BigInteger no = new BigInteger(1, messageDigest); 
  
            // Convert message digest into hex value 
            String hashtext = no.toString(16); 
  
            while (hashtext.length() < 32) { 
                hashtext = "0" + hashtext; 
            } 
  
            return hashtext; 
        } 
  
        // For specifying wrong message digest algorithms 
        catch (NoSuchAlgorithmException e) { 
            System.out.println("Exception thrown"
                               + " for incorrect algorithm: " + e); 
  
            return null; 
        } 
    } 

}

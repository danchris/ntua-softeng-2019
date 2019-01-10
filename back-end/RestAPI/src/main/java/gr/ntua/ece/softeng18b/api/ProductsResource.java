package gr.ntua.ece.softeng18b.api;

import gr.ntua.ece.softeng18b.conf.Configuration;
import gr.ntua.ece.softeng18b.data.DataAccess;
import gr.ntua.ece.softeng18b.data.Limits;
import gr.ntua.ece.softeng18b.data.model.Product;
import org.restlet.data.Form;
import org.restlet.data.Header;
import org.restlet.representation.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductsResource extends ServerResource {

    private final DataAccess dataAccess = Configuration.getInstance().getDataAccess();

    @Override
    protected Representation get() throws ResourceException {
    	String startAttr	= getQuery().getValues("start");
    	String countAttr	= getQuery().getValues("count");
    	String statusAttr	= getQuery().getValues("status");
    	String sortAttr 	= getQuery().getValues("sort");
    	String formatAttr	= getQuery().getValues("format");
    	
    	if(formatAttr!=null && !formatAttr.equals("json")) throw new ResourceException(400,"Only json format is supported at the moment");
    	
    	int start, count, status;
    	String sort = sortAttr;
    	
        try {
            start = Integer.parseInt(startAttr);
        } catch(NumberFormatException e) {
        	start = 0; //default
        }
        //////////////////////////////////////////////
        try {
        	if(sort == null) throw  new NumberFormatException("The sort attribute entered, " + sort + " is invalid."); 
            if(sort.equals("id|ASC")) sort = "id ASC";
            else if(sort.equals("id|DESC")) sort = "id DESC";
            else if(sort.equals("name|ASC")) sort = "name ASC";
            else if(sort.equals("name|DESC")) sort = "name DESC";
            else throw  new NumberFormatException("The sort attribute entered, " + sort + " is invalid."); 
        } catch(NumberFormatException e) {
        	sort = "id DESC"; //default
        }
        //////////////////////////////////////////////
    
        try {
            count = Integer.parseInt(countAttr);
        } catch(NumberFormatException e) {
        	count = 10; //default
        }
        //////////////////////////////////////////////

        try {
        	if(statusAttr == null) throw  new NumberFormatException("The sort attribute entered, " + sort + " is invalid."); 
        	if(statusAttr.equals("ACTIVE")) status = 0;
            else if (statusAttr.equals("WITHDRAWN")) status = 1;
            else if (statusAttr.equals("ALL")) status = -1; // -1 for all products
            else throw  new NumberFormatException("The status attribute entered, " + sort + " is invalid."); 
        } catch(NumberFormatException e) {
        	status = 0; //default
        }
        //////////////////////////////////////////////

        
    	
        List<Product> products = dataAccess.getProducts(new Limits(start,count),status,sort);

        Map<String, Object> map = new HashMap<>();
        map.put("start", start);
        map.put("count", count);
        map.put("total", products.size());
        map.put("products", products);

        return new JsonMapRepresentation(map);
    }

    @Override
    protected Representation post(Representation entity) throws ResourceException {

        //Create a new restlet form
        Form form = new Form(entity);
        //Read the parameters
        String name = form.getFirstValue("name");
        String description = form.getFirstValue("description");
        String category = form.getFirstValue("category");
        String tags = form.getFirstValue("tags");
        
        //authorization of user
        Series<Header> headers = (Series<Header>) getRequestAttributes().get("org.restlet.http.headers");
        String user_token = headers.getFirstValue("X-OBSERVATORY-AUTH");
        //System.out.println(">>>>>>>000 User token is: "+user_token);
        if(user_token == null || user_token.isEmpty()) throw new ResourceException(401, "Not authorized to add product");
        if(!dataAccess.isLogedIn(user_token))throw new ResourceException(401, "Not authorized to add product");
        //System.out.println(">>>>>>>>>> User token is: "+user_token);

        //validate the values (in the general case)
        if(name == null || description == null || category == null) throw new ResourceException(400,"This operation needs more parameters");
        if(tags == null) tags = "";
        //String regex = "^[a-zA-Z0-9\\s.\\-.\\,.\\'.\\[.\\[.\\(.\\).\\..\\+.\\-.\\:.\\@]+$";
        //String regex_s = "^[a-zA-Z0-9\\s.\\-.\\,.\\'.\\[.\\[.\\(.\\)]+$";
        //if(!name.matches(regex) || !description.matches(regex) || !category.matches(regex_s) || !tags.matches(regex_s) ) throw new ResourceException(400);
        
        try{
        	Product product = dataAccess.addProduct(name, description, category, false, tags);
        	return new JsonProductRepresentation(product);
        }
        catch(org.springframework.dao.DuplicateKeyException e){
        	throw new ResourceException(400,"This product already exists in the database");
        }

        
    }
    
}

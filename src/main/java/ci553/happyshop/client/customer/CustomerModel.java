package ci553.happyshop.client.customer;

import ci553.happyshop.catalogue.Order;
import ci553.happyshop.catalogue.Product;
import ci553.happyshop.storageAccess.DatabaseRW;
import ci553.happyshop.orderManagement.OrderHub;
import ci553.happyshop.utility.StorageLocation;
import ci553.happyshop.utility.ProductListFormatter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * TODO
 * You can either directly modify the CustomerModel class to implement the required tasks,
 * or create a subclass of CustomerModel and override specific methods where appropriate.
 */
public class CustomerModel {
    public CustomerView cusView;
    public DatabaseRW databaseRW; //Interface type, not specific implementation
                                  //Benefits: Flexibility: Easily change the database implementation.
    public RemoveProductNotifier removeProductNotifier; // Notifier for insufficient stock

    private Product theProduct =null; // product found from search
    private ArrayList<Product> trolley =  new ArrayList<>(); // a list of products in trolley
    // Four UI elements to be passed to CustomerView for display updates.
    private String imageName = "imageHolder.jpg";                // Image to show in product preview (Search Page)
    private String displayLaSearchResult = "No Product was searched yet"; // Label showing search result message (Search Page)
    private String displayTaTrolley = "";                                // Text area content showing current trolley items (Trolley Page)
    private String displayTaReceipt = "";                                // Text area content showing receipt after checkout (Receipt Page)

    //SELECT productID, description, image, unitPrice,inStock quantity
    void search() throws SQLException {
        String productId = cusView.tfId.getText().trim();
        String productName = cusView.tfName.getText().trim();

        // Determine which search term to use (prefer ID if both are provided)
        String keyword;
        if (!productId.isEmpty()) {
            keyword = productId;
        } else {
            keyword = productName;
        }

        if(!keyword.isEmpty()){
            ArrayList<Product> results = databaseRW.searchProduct(keyword); //search database by ID or name

            // If multiple products found (name search result), take the first one
            if(!results.isEmpty()){
                theProduct = results.get(0);

                if(theProduct.getStockQuantity()>0){
                    double unitPrice = theProduct.getUnitPrice();
                    String description = theProduct.getProductDescription();
                    String actualId = theProduct.getProductId();
                    int stock = theProduct.getStockQuantity();

                    String baseInfo = String.format("Product_Id: %s\n%s,\nPrice: £%.2f", actualId, description, unitPrice);
                    String quantityInfo = stock < 100 ? String.format("\n%d units left.", stock) : "";
                    displayLaSearchResult = baseInfo + quantityInfo;
                    System.out.println(displayLaSearchResult);
                }
                else{
                    theProduct=null;
                    displayLaSearchResult = "Product found but out of stock";
                    System.out.println("Product found but out of stock");
                }
            }
            else{
                theProduct=null;
                displayLaSearchResult = "No Product was found with keyword: " + keyword;
                System.out.println("No Product was found with keyword: " + keyword);
            }
        }else{
            theProduct=null;
            displayLaSearchResult = "Please type Product ID or Name";
            System.out.println("Please type Product ID or Name.");
        }
        updateView();
    }

    void addToTrolley() {
        if (theProduct != null) {

            // trolley.add(theProduct) — Product is appended to the end of the trolley.
            // To keep the trolley organized, add code here or call a method that:
            // 1. Merges items with the same product ID (combining their quantities).
            // 2. Sorts the products in the trolley by product ID.

            // Check if the product already exists
            boolean found = false;
            for (Product p : trolley) {
                if (p.getProductId().equals(theProduct.getProductId())) {
                    // If so, increment quantity by 1
                    p.setOrderedQuantity(p.getOrderedQuantity() + 1);
                    found = true;
                    break;
                }
            }

            // If not found, add new product with quantity 1
            if (!found) {
                theProduct.setOrderedQuantity(1);
                trolley.add(theProduct);
            }


            // Sort items in trolly by product ID
            trolley.sort((p1, p2) -> p1.getProductId().compareTo(p2.getProductId()));

            displayTaTrolley = ProductListFormatter.buildString(trolley); //build a String for trolley so that we can show it
        }

        else{
            displayLaSearchResult = "Please search for an available product before adding it to the trolley";
            System.out.println("must search and get an available product before add to trolley");
        }
        displayTaReceipt=""; // Clear receipt to switch back to trolleyPage (receipt shows only when not empty)
        updateView();
    }

    void checkOut() throws IOException, SQLException {
        if(!trolley.isEmpty()){
            // Group the products in the trolley by productId to optimize stock checking
            // Check the database for sufficient stock for all products in the trolley.
            // If any products are insufficient, the update will be rolled back.
            // If all products are sufficient, the database will be updated, and insufficientProducts will be empty.
            // Note: If the trolley is already organized (merged and sorted), grouping is unnecessary.
            ArrayList<Product> groupedTrolley= groupProductsById(trolley);
            ArrayList<Product> insufficientProducts= databaseRW.purchaseStocks(groupedTrolley);

            if(insufficientProducts.isEmpty()){ // If stock is sufficient for all products
                //get OrderHub and tell it to make a new Order
                OrderHub orderHub =OrderHub.getOrderHub();
                Order theOrder = orderHub.newOrder(trolley);
                trolley.clear();
                displayTaTrolley ="";
                displayTaReceipt = String.format(
                        "Order_ID: %s\nOrdered_Date_Time: %s\n%s",
                        theOrder.getOrderId(),
                        theOrder.getOrderedDateTime(),
                        ProductListFormatter.buildString(theOrder.getProductList())
                );
                System.out.println(displayTaReceipt);

                // Close notifier window if showing from previous checkout attempt
                if(removeProductNotifier != null) {
                    removeProductNotifier.closeNotifierWindow();
                }
            }
            else{ // Some products have insufficient stock — build an error message to inform the customer
                StringBuilder errorMsg = new StringBuilder();
                for(Product p : insufficientProducts){
                    errorMsg.append("\u2022 "+ p.getProductId()).append(", ")
                            .append(p.getProductDescription()).append(" (Only ")
                            .append(p.getStockQuantity()).append(" available, ")
                            .append(p.getOrderedQuantity()).append(" requested)\n");
                }
                theProduct=null;

                // Remove products with insufficient stock from the trolley
                trolley.removeIf(product -> {
                    for(Product insufficientProd : insufficientProducts) {
                        if(product.getProductId().equals(insufficientProd.getProductId())) {
                            return true;
                        }
                    }
                    return false;
                });

                // Update trolley display
                displayTaTrolley = ProductListFormatter.buildString(trolley);

                // Show notification window with removal message
                if(removeProductNotifier != null) {
                    removeProductNotifier.showRemovalMsg(errorMsg.toString());
                }

                System.out.println("stock is not enough");
            }
        }
        else{
            displayTaTrolley = "Your trolley is empty";
            System.out.println("Your trolley is empty");
        }
        updateView();
    }

    /**
     * Groups products by their productId to optimize database queries and updates.
     * By grouping products, we can check the stock for a given `productId` once, rather than repeatedly
     */
    private ArrayList<Product> groupProductsById(ArrayList<Product> proList) {
        Map<String, Product> grouped = new HashMap<>();
        for (Product p : proList) {
            String id = p.getProductId();
            if (grouped.containsKey(id)) {
                Product existing = grouped.get(id);
                existing.setOrderedQuantity(existing.getOrderedQuantity() + p.getOrderedQuantity());
            } else {
                // Make a copy to avoid modifying the original
                Product copy = new Product(p.getProductId(),p.getProductDescription(),
                        p.getProductImageName(),p.getUnitPrice(),p.getStockQuantity());
                copy.setOrderedQuantity(p.getOrderedQuantity());
                grouped.put(id, copy);
            }
        }
        return new ArrayList<>(grouped.values());
    }

    void removeFromTrolley() {
        if (theProduct != null) {
            //check if product is in trolley
            Product productInTrolley = null;
            for (Product p : trolley) {
                if (p.getProductId().equals(theProduct.getProductId())) {
                    productInTrolley = p;
                    break;
                }
            }

            if (productInTrolley != null) {
                //if product is in trolley, decrease quantity by 1, if it has a quantity greater than 1
                if (productInTrolley.getOrderedQuantity() > 1) {
                    productInTrolley.setOrderedQuantity(productInTrolley.getOrderedQuantity() - 1);
                    System.out.println("Decreased quantity of " + theProduct.getProductId() + " to " + productInTrolley.getOrderedQuantity());
                } else {
                    // if the quantity is only 1, remove it from trolley
                    trolley.remove(productInTrolley);
                    System.out.println("Removed " + theProduct.getProductId() + " from trolley");
                }
                displayTaTrolley = ProductListFormatter.buildString(trolley); //update trolley display
            } else {
                //if product is not in trolley yet and user tries to decrease quantity
                System.out.println("Item isn't in trolley yet");
            }
        //if nothing is 'selected' in the search bar
        } else {
            System.out.println("Please search for a product first");
        }
        displayTaReceipt=""; //clear receipt to show trolley page
        updateView();
    }

    void cancel(){
        trolley.clear();
        displayTaTrolley="";

        // Close notifier window if showing
        if(removeProductNotifier != null) {
            removeProductNotifier.closeNotifierWindow();
        }

        updateView();
    }
    void closeReceipt(){
        displayTaReceipt="";
    }

    void updateView() {
        if(theProduct != null){
            imageName = theProduct.getProductImageName();
            String relativeImageUrl = StorageLocation.imageFolder +imageName; //relative file path, eg images/0001.jpg
            // Get the full absolute path to the image
            Path imageFullPath = Paths.get(relativeImageUrl).toAbsolutePath();
            imageName = imageFullPath.toUri().toString(); //get the image full Uri then convert to String
            System.out.println("Image absolute path: " + imageFullPath); // Debugging to ensure path is correct
        }
        else{
            imageName = "imageHolder.jpg";
        }
        cusView.update(imageName, displayLaSearchResult, displayTaTrolley,displayTaReceipt);
    }
     // extra notes:
     //Path.toUri(): Converts a Path object (a file or a directory path) to a URI object.
     //File.toURI(): Converts a File object (a file on the filesystem) to a URI object

    //for test only
    public ArrayList<Product> getTrolley() {
        return trolley;
    }
}

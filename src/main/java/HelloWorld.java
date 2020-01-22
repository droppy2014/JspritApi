import com.google.gson.*;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.job.Shipment;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;

import org.json.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;

import static spark.Spark.*;

public class HelloWorld {

    static JSONArray jobs;
    static JSONArray vehicles;
    static JSONArray locations;
    static VehicleRoutingProblem.Builder vrpBuilder;
    static VehicleRoutingTransportCostsMatrix.Builder costMatrixBuilder;

    public static void main(String[] args) {

        port(1234);
        //stop();

        //get("/hello", (req, res) -> "Hello World!!xx!");

        post("/hello", (request, response) -> {
            response.type("application/json");
            jobs = null;
            vehicles = null;
            locations = null;
            vrpBuilder =  VehicleRoutingProblem.Builder.newInstance();
            costMatrixBuilder = VehicleRoutingTransportCostsMatrix.Builder.newInstance(true);



            //getMatrix();

            //VehicleRoutingProblemSolution bestSolution = (VehicleRoutingProblemSolution) findBestSolution();
            //String res = request.body();

            try {
                JSONObject responceObj = new JSONObject(request.body());
                jobs = responceObj.getJSONArray("jobs");
                vehicles = responceObj.getJSONArray("vehicles");
                locations = responceObj.getJSONArray("locations");
                createMatrix();
                createJobs();
                createVehicles();
                createSolution();

            } catch (JSONException e) {
                // log or consume it some other way
                System.out.println(e);
            }
            return new Gson().toJson("ok");
        });

    }


    private static void createMatrix() {
        String coords_line = "";
        for (int j = 0 ; j < locations.length(); j++) {
            JSONObject location = locations.getJSONObject(j);
            JSONArray location_coord = location.getJSONArray("location");
            float location_coord_lat = location_coord.getFloat(0);
            float location_coord_lng = location_coord.getFloat(1);
            coords_line = coords_line + ";" + location_coord_lat + "," + location_coord_lng;
            //System.out.println("matrix_cell_value " + matrix_cell_value);
        }
        coords_line = coords_line.substring(1);
        System.out.println(coords_line);
        requestMatrix(coords_line);
    }

    private static void requestMatrix(String coords_line) {
        String sURL = "http://95.217.33.235:5000/table/v1/driving/"+coords_line+"?annotations=duration,distance";
        try {
            URL url = new URL(sURL);
            URLConnection request = url.openConnection();
            request.connect();
            JsonParser jp = new JsonParser(); //from gson
            JsonElement root = jp.parse(new InputStreamReader((InputStream) request.getContent())); //Convert the input stream to a json element
            JsonObject gson_matrix_obj = root.getAsJsonObject();
            JSONObject matrix_obj = new JSONObject(gson_matrix_obj.toString());
            createTimeMatrix(matrix_obj);
            createDistanceMatrix(matrix_obj);
            System.out.println("responce " + matrix_obj);

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createTimeMatrix(JSONObject matrix_obj) {

        //VehicleRoutingTransportCostsMatrix.Builder costMatrixBuilder = VehicleRoutingTransportCostsMatrix.Builder.newInstance(true);

        JSONArray durations = matrix_obj.getJSONArray("durations");
        for (int r = 0 ; r < durations.length(); r++) {
            JSONArray matrix_row = durations.getJSONArray(r);
            for (int c = 0 ; c < matrix_row.length(); c++) {
                float matrix_cell_value = matrix_row.getFloat(c);
                String row_num = String.valueOf(r);
                String cell_num = String.valueOf(c);
                //costMatrixBuilder.addTransportTime(row_num, cell_num, matrix_cell_value);

                JSONObject loc_json_from = (JSONObject) locations.get(r);
                String from_id = loc_json_from.getString("id");

                JSONObject loc_json_to = (JSONObject) locations.get(c);
                String to_id = loc_json_to.getString("id");

                costMatrixBuilder.addTransportTime(from_id, to_id, matrix_cell_value);


                //System.out.println("matrix_cell_value " + matrix_cell_value);
            }
        }
    }

    private static void createDistanceMatrix(JSONObject matrix_obj) {

        JSONArray distances = matrix_obj.getJSONArray("distances");
        for (int r = 0 ; r < distances.length(); r++) {
            JSONArray matrix_row = distances.getJSONArray(r);
            for (int c = 0 ; c < matrix_row.length(); c++) {
                float matrix_cell_value = matrix_row.getFloat(c);
                String row_num = String.valueOf(r);
                String cell_num = String.valueOf(c);


                JSONObject loc_json_from = (JSONObject) locations.get(r);
                String from_id = loc_json_from.getString("id");

                JSONObject loc_json_to = (JSONObject) locations.get(c);
                String to_id = loc_json_to.getString("id");

                costMatrixBuilder.addTransportDistance(from_id, to_id, matrix_cell_value);

                System.out.println("'" + from_id  + "' to '" + to_id   + "' = " + matrix_cell_value);
            }
        }
    }

    private static void createJobs() {
        for (int j = 0 ; j < jobs.length(); j++) {
            JSONObject job = jobs.getJSONObject(j);
            String pickup_from = job.getString("pickup_from");
            String delivery_to = job.getString("delivery_to");
            String job_id = String.valueOf(job.getInt("id"));
            System.out.println("start shipment");
            Shipment shipment = Shipment.Builder.newInstance(job_id)
                    .setName("myShipment")
                    .setPickupLocation(Location.newInstance(pickup_from))
                    .setDeliveryLocation(Location.newInstance(delivery_to))
                    .addSizeDimension(0,3)
                    .addSizeDimension(1,7)
                    //.addRequiredSkill("loading bridge").addRequiredSkill("electric drill")
                    .build();
            System.out.println("shipment builded");
            System.out.println(job_id);
            vrpBuilder.addJob(shipment);
            System.out.println("shipment added");
            System.out.println("#############");

        }
    }

    private static void createVehicles() {
        for (int i = 0 ; i < vehicles.length(); i++) {
            JSONObject vehicle = vehicles.getJSONObject(i);
            addVehicle(vehicle);
        }
    }



    private static void addVehicle(JSONObject vehicle) {

        System.out.println("extract vehicle");
        System.out.println(vehicle);

        VehicleTypeImpl vehicleType = VehicleTypeImpl.Builder.newInstance("vehicleType")
                .addCapacityDimension(0,30).addCapacityDimension(1,100)
                .build();
        VehicleImpl new_vehicle = VehicleImpl.Builder.newInstance(String.valueOf(vehicle.getInt("id")))
                .setType(vehicleType)
                .setStartLocation(Location.newInstance(vehicle.getString("start"))).setEndLocation(Location.newInstance(vehicle.getString("end")))
                //.addSkill("loading bridge").addSkill("electric drill")
                .build();
        vrpBuilder.addVehicle(new_vehicle);
    }

    private static void createSolution() {

        VehicleRoutingTransportCosts costMatrix = costMatrixBuilder.build();
        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE).setRoutingCost(costMatrix);
        VehicleRoutingProblem problem =  vrpBuilder.build();
        VehicleRoutingAlgorithm algorithm = Jsprit.createAlgorithm(problem);
        // search solutions
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        // get best
        VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);


        SolutionPrinter.print(problem, bestSolution, SolutionPrinter.Print.CONCISE);
        SolutionPrinter.print(problem, bestSolution, SolutionPrinter.Print.VERBOSE);
        // define an algorithm out of the box - this creates a large neighborhood search algorithm




    }


}

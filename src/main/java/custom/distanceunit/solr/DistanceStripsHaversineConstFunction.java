package custom.distanceunit.solr;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.DoubleDocValues;
import org.apache.lucene.queries.function.valuesource.VectorValueSource;
import org.apache.lucene.search.IndexSearcher;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DistanceStripsHaversineConstFunction extends ValueSource {

    private final double latCenter;
    private final double lonCenter;
    private final VectorValueSource p2;
    private final ValueSource latSource;
    private final ValueSource lonSource;
    private final double thickness;
    private final double stripAmount;
    private final double latCenterRadCos;
    private List<Integer> dynamicThickness = null;
    private static final double EARTH_MEAN_DIAMETER = 12742.0175428D;


    public DistanceStripsHaversineConstFunction(double latCenter, double lonCenter, String thickness, VectorValueSource vs){
        this.latCenter = latCenter;
        this.lonCenter = lonCenter;
        this.p2 = vs;
        this.latSource = this.p2.getSources().get(0);
        this.lonSource = this.p2.getSources().get(1);
        this.latCenterRadCos = Math.cos(latCenter*0.017453292519943295D);
        this.stripAmount = thickness.split(",").length+1;
        this.thickness = 0;
        dynamicThickness = Arrays.asList(thickness.split(","))
                .stream()
                .map(s -> Integer.valueOf(s))
                .collect(Collectors.toList());
    }

    public DistanceStripsHaversineConstFunction(double latCenter, double lonCenter, double thickness, double stripAmount, VectorValueSource vs){
        this.latCenter = latCenter;
        this.lonCenter = lonCenter;
        this.thickness = thickness;
        this.stripAmount = stripAmount;
        this.p2 = vs;
        this.latSource = this.p2.getSources().get(0);
        this.lonSource = this.p2.getSources().get(1);
        this.latCenterRadCos = Math.cos(latCenter*0.017453292519943295D);

    }



    protected String name(){
        return "DistanceStrip";
    }


    @Override
    public FunctionValues getValues(Map map, LeafReaderContext leafReaderContext) throws IOException {
        final FunctionValues latVals = latSource.getValues(map, leafReaderContext);
        final FunctionValues lonVals = lonSource.getValues(map, leafReaderContext);
        final double latCenterRad = latCenter*0.017453292519943295D;
        final double lonCenterRad = lonCenter*0.017453292519943295D;
        final double latCenterRadCos = this.latCenterRadCos;

        return new DoubleDocValues(this) {
            @Override
            public double doubleVal(int i) throws IOException {
                try{
                    double latRad = latVals.doubleVal(i)*0.017453292519943295D;
                    double lonRad = lonVals.doubleVal(i)*0.017453292519943295D;
                    double diffX = latCenterRad - latRad;
                    double diffY = lonCenterRad - lonRad;
                    double hsinX = Math.sin(diffX*0.5D);
                    double hsinY = Math.sin(diffY*0.5D);
                    double h = hsinX*hsinX + latCenterRadCos*Math.cos(latRad)*hsinY*hsinY;
                    double distance = 12742.0175428D * Math.atan2(Math.sqrt(h), Math.sqrt(1.0D - h));

                    if(dynamicThickness != null){
                        for(int ix =0; ix<stripAmount-1;ix++){
                            if(distance>=0 && distance <=dynamicThickness.get(ix)){
                                return 100*(ix+1);
                            }
                        }
                        return stripAmount*100;
                    }

                    for(int ix = 0; ix<stripAmount;ix++){
                        if(distance>=0 && distance <= thickness*(ix+1)){
                            return 100*ix;
                        }
                    }
                    return stripAmount*100;

                }catch (IOException ioe){
                    ioe.printStackTrace();
                    return 0;
                }
            }

            public String toString(int doc){
                try{
                    return DistanceStripsHaversineConstFunction.this.name()
                            + "("
                            + latVals.toString(doc)
                            + ","
                            + lonVals.toString(doc)
                            + ","
                            + DistanceStripsHaversineConstFunction.this.latCenter
                            + ","
                            + DistanceStripsHaversineConstFunction.this.lonCenter
                            +")";
                }catch (IOException ioe){
                    ioe.printStackTrace();
                    return "I/O Exception on toString";
                }
            }
        };
    }


    public void createWeight(Map context, IndexSearcher indexSearcher) throws IOException{
        this.latSource.createWeight(context,indexSearcher);
        this.lonSource.createWeight(context,indexSearcher);
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof DistanceStripsHaversineConstFunction)){
            return false;
        }
        DistanceStripsHaversineConstFunction other = (DistanceStripsHaversineConstFunction)o;
        return this.latCenter == other.latCenter &&
                this.lonCenter == other.lonCenter &&
                this.p2.equals(other.p2);
    }

    @Override
    public int hashCode() {
        int result = this.p2.hashCode();
        long temp = Double.doubleToRawLongBits(this.latCenter);
        result = 31*result + (int)(temp ^ temp >>> 32);
        temp = Double.doubleToRawLongBits(this.lonCenter);
        result = 31*result + (int)(temp ^ temp >>> 32);
        return result;
    }

    @Override
    public String description() {
        return this.name() + "(" + this.p2 + "," + this.latCenter + "," + this.lonCenter + ")";
    }
}

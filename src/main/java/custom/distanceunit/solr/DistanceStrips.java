package custom.distanceunit.solr;

import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.*;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.solr.common.params.SpatialParams;
import org.apache.solr.schema.AbstractSpatialFieldType;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.search.ValueSourceParser;
import org.apache.solr.util.DistanceUnits;
import org.apache.solr.util.SpatialUtils;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DistanceStrips extends ValueSourceParser {


    @Override
    public ValueSource parse(FunctionQParser functionQParser) throws SyntaxError {


        List<ValueSource> sources = functionQParser.parseValueSourceList();

        MultiValueSource mvs1 = parsePoint(functionQParser);
        MultiValueSource mvs2 = parseSField(functionQParser);
        double thickness = 5;//Default value
        double stripAmount = 3;//Default value
        boolean dynamic = false;
        String dynamicStrips = null;

        if(sources.size() > 2){
            throw new SyntaxError("DistanceStrips - Too Many Params: " + sources);
        }else if(sources.size() == 2){
            thickness = ((ConstNumberSource)sources.get(0)).getDouble();
            stripAmount = ((ConstNumberSource)sources.get(1)).getDouble();
        }else if(sources.size() == 1){
            if(sources.get(0) instanceof LiteralValueSource){
                if(((LiteralValueSource)sources.get(0)).getValue().contains(",")){
                    dynamic = true;
                    dynamicStrips = ((LiteralValueSource)sources.get(0)).getValue();
                }else{
                    throw new SyntaxError("DistanceStrips - Invalid Params: " + sources);
                }
            }else if(sources.get(0) instanceof ConstNumberSource){
                thickness = ((ConstNumberSource) sources.get(0)).getDouble();
            }else{
                throw new SyntaxError("DistanceStrips - Invalid Params: " + sources);
            }
        }

        double[] constants = getConstants(mvs1);


        if(constants != null && mvs2 instanceof VectorValueSource){
            if(dynamic){
                return new DistanceStripsHaversineConstFunction(constants[0], constants[1], dynamicStrips, (VectorValueSource) mvs2);
            }
            return new DistanceStripsHaversineConstFunction(constants[0], constants[1], thickness, stripAmount, (VectorValueSource)mvs2);
        }else{
            throw new SyntaxError("DistanceStrips - Invalid Params: " + sources);
        }
    }


    private MultiValueSource parsePoint(FunctionQParser fqp) throws SyntaxError{

        String ptStr = fqp.getParam(SpatialParams.POINT);
        if(ptStr == null){
            return null;
        }
        Point point = SpatialUtils.parsePointSolrException(ptStr, SpatialContext.GEO);
        return new VectorValueSource(Arrays.asList(new DoubleConstValueSource(point.getY()), new DoubleConstValueSource(point.getX())));
    }


    private double[] getConstants(MultiValueSource mvs){
        if(!(mvs instanceof VectorValueSource)){
            return null;
        }

        List<ValueSource> sources = ((VectorValueSource) mvs).getSources();
        if(sources.get(0) instanceof ConstNumberSource && sources.get(1) instanceof ConstNumberSource){
            return new double[]{((ConstNumberSource) sources.get(0)).getDouble(),((ConstNumberSource) sources.get(1)).getDouble()};
        }
        return null;
    }

    private MultiValueSource parseSField(FunctionQParser fqp) throws SyntaxError{
        String sField = fqp.getParam(SpatialParams.FIELD);

        if(sField == null){
            return null;
        }


        SchemaField sf = fqp.getReq().getSchema().getField(sField);
        FieldType sfType = sf.getType();

        if(sfType instanceof AbstractSpatialFieldType){
            AbstractSpatialFieldType asft = (AbstractSpatialFieldType) sfType;
            return new DistanceStrips.SpatialStrategyMultiValueSource(asft.getStrategy(sField), asft.getDistanceUnits());
        }

        ValueSource vs = sfType.getValueSource(sf,fqp);
        if(vs instanceof MultiValueSource){
            return (MultiValueSource) vs;
        }

        throw new SyntaxError("Distance Strips - Spatial Field must implement MultiValueSouurce or extend AbstractSpatialFieldType: " + sf);

    }

    private static class SpatialStrategyMultiValueSource extends VectorValueSource{
        final SpatialStrategy strategy;
        final DistanceUnits distanceUnits;

        public SpatialStrategyMultiValueSource(SpatialStrategy strategy, DistanceUnits distanceUnits){
            super(Collections.EMPTY_LIST);
            this.distanceUnits = distanceUnits;
            this.strategy = strategy;

        }

        @Override
        public List<ValueSource> getSources(){
            throw new IllegalStateException();
        }
    }
}

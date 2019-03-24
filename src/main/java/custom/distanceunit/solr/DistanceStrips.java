package custom.distanceunit.solr;

import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.ConstNumberSource;
import org.apache.lucene.queries.function.valuesource.DoubleConstValueSource;
import org.apache.lucene.queries.function.valuesource.MultiValueSource;
import org.apache.lucene.queries.function.valuesource.VectorValueSource;
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
        return null;
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

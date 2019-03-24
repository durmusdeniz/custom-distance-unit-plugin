# custom-distance-unit-plugin
A plugin to create your own distance units for spatial search on Apache Solr.

Default distance handlers are customized for creating your own distance units and sorting methods on top of it. 

After placing the jar file into your shared lib of Solr installation, simply, add the below line to the solrconfig.xml file:

<valueSourceParser name="strips" class="custom.distanceunit.solr.DistanceStrips" />

A sample use case might be the story below:

Well assume that you are city center, and looking for a restaurant. As it is rush hour, although some restaurants 
appear to be closer to you, it could be actually harder to reach there.

Restaurants are within 5 mins from us. ( 0 to 1 km)
Restaurants are within 10 mins from us (1 km to 4 km)
Restaurants are within 20 mins from us (4 km to 5 km)
Restaurants are more than 30 mins from us (5+ km)

While we can use the default geofilt function to sort restaurants, it will not be able to sort the restaurants based on the imaginary
distance strips above. So for this case we need a custom distance function.

Once the configs are done, you can add the newly created custom value parser for geofilt. Just add _strips_:strips("1,4,5") 
to your fl while keeping the rest of the params the same for geofilt, for the case mentioned above. 
You can also use it for sorting.

If you check the code carefully, you will see that it can also support queries with identical strip sizes. 
In order to use it in that way, simply use it with two integers, first one is for the width and the second for the amount of 
the strips: strips(2,5) - 5 strips with 2 km width.



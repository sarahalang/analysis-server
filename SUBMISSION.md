# Documentation of important design decisions

## SensorManager
After reading the instructions and inspecting the given data, we pretty quickly decided on implementing a Sensormanager.
Since the Folders and csv files would act like a pseudo database, we intended this class to be some kind of database connection.
Due to this fact it was pretty obvious to implement this class as a Singleton.  
The class would hold a Hash Map which uses the IDs of the Sensors as Key, and a vector of the instances as values.
This way of storing is efficient and fast when accessing the Sensors later in the process.
When a query reaches the server, the sensor manager class checks if this Hash map is already initialized.
If this has not been done yet, the file tree is scanned for available sensors and their metrics.  
Sensor Manager is also home to our Cache implementation.

## ClientConnection
For the server client connection we used Java socket programming. The task was to be able to accept more than one client 
simultaneously. To achieve this we implemented a multithreaded server client connection, where we have a ClientHandlerThread
which accepts one client and gets triggered in the AnalysisServer. 
After that we used the DataInputStream and ObjectOutputStream to read/write from/to the client.

## ClientCommand
In order to make the implementation as dynamic as possible, the commands received from the client are wrapped into
a separate class. This class helps the server to keep track of its current state. For example if the Operation of the
Data Series the Server is currently working does not match the operation in the command, the Server knows that
the Data Series has to be further processed before being returned to the Client.

## Data Command 
The data command is the base for all other implemented commands, but the ls command.  
Based on the Hash Map held by the Sensor Manager Class, we are terminating if the requested sensor as well as the
requested metric are matching with an existing sensor. If we find the sensor, the stored information about it,
helps us to locate its data within the file tree. Since the folders and files match a pattern, we are able to 
build their paths out of the sensors attributes. (ID_type/date_type_sensor_id.csv)  
This way we can avoid useless and time expensive searches within the file tree.  
When data is found, the requested time period is extracted (if not cached) from the csv files. If intervals or
operations are requested, they are executed after the full data for the requested time period is extracted.  
After all operations were executed, the Server returns the sampled Data Series back to the Client.

## LS Command 
This command wraps up the Hash Map held by Sensor Manager and returns it to the Client.

## Linechart
The Linechart class is based on the Diagram (former Picture) class. The Diagram class contains general methods and attributes
which are used for the Linechart and the Scatterplot. To save the diagram we use the Buffered Image and Graphics2d 
Standard Library because we aren't allowed to use third party library to draw our charts.

`linechart 4795 temperature 2018-01-01T00:00:00 2018-02-01T00:30:00 /app/linechart.png MIN 1m`

`linechart 4795 temperature 2018-01-01T00:00:00 2018-01-03T00:30:00 /app/linechart.png`

`linechart 3841 P1 2018-01-01T00:00:00 2018-01-03T00:30:00 /app/linechart.png`

## Scatterplot
The Scatterplot class is based on the Diagram (former Picture) class.The Diagram class contains general methods and attributes
which are used for the Linechart and the Scatterplot. To save the Scatterplot we use the Buffered Image and Graphics2d 
Standard Library because we aren't allowed to use third party library to draw our charts.

`scatterplot 1000 P1 1000 P2 2018-01-01T00:00:00 2018-01-01T00:30:00 /app/scatterplot.png`

`scatterplot 1000 P1 1000 P2 2018-01-01T00:00:00 2018-01-01T00:00:01 /app/scatterplot.png`

## Cache 
The logic of the cache is as follows. Each server instance gets a unique identifiert (String of `sensorID` and `metric`). This is used as the key to a `HashMap` storing one cache (realized as `Vector<CacheElement>`) per sensor instance. That way, when the `getSensorData()` function is run, we quickly check whether this sensor already has a cache. If no, we don't even need to look further. In this case, the normal reading operations from the CSV file are run. Those usually only return the timestamps required per the client's command. However, in the loop where the timestamps are searched in the CSV file, the whole of the file is read anyway. This is why we decided that the cache should just store all values of the current CSV file whenever a CSV file is read that wasn't in the cache yet. 

If it was found that there exists a cache for the sensor in question, the cache is queried to see if it actually already contains all the values we want from it. We decided that we consider it a CacheHit when 

- a) the values in the cache and the values for a query are an exact match, or 
- b) when the values queried are a subset of the values in the cache. 
All other cases are considered CacheMisses. CacheHits are retrieved from the `CacheElements` (which contain one `DataSeries` per `CacheElement`) by using `TreeSet`'s `subset()` function on the DataSeries. 

The decision to cache each queried CSV file as a whole and that of only accepting subsets or exact matches as CacheHits go hand in hand: Had we just added partial CSV (just the required data points) and the amount of data points is really small, then we would likely often get Partial Hits. Since we thought that accepting partial hits would likely result in worse performance without any gains, we did not consider those cases. 

This way of doing the cacheing was allowed as per [this TeachCenter post.](https://tc.tugraz.at/main/mod/forum/discuss.php?d=28163)


## User Interface
The user interface was designed to be fully functional when the Logger is out. This means that when the Logger is on (outputhidden = false), there will be lots of outputs. However, the interface offers a nice overview for the user when the Logger is off.

- **Data Command:** It will list the points queried for `NONE` and results for the other possible operations. It will restate/repeat the user query in table form, so the user doesn't get confused as to - for example - what metrics were queried, etc. 
- **LS Command:** A table overview is given for all the sensors queried.
- **Cluster Command:** The user will see a summary of the given parameters. Also the client will notify the user about possible Warnings or Errors that might occur, as well as when the query computation has finished.


## Error Handling

There are several cases which can cause an error.  In order to warn the user about this, we created empty clusters containing the error message.

If there are IDs requested which are not available or do not match the given metric, we only process the valid part of the query. This is also true if the given parameters would create a series which does not have enough data (dangeling points).

If data is missing we choose to recursively recreate the missing data by either calculating the mean over its neighbors or choosing its nearest neighbor if only one is available.

Depending on the severity of the error, the user is either warned while the correct parts 
## SOM algorithm
The SOM algorithm is based on the data command from assignment 1. The implementation follows the description on the slides [lecture slides](https://tc.tugraz.at/main/pluginfile.php/142869/mod_folder/content/0/OOP2VO-2020-11-05-Data-Analysis-Concepts.pdf) starting from slide 27.
For the neighbourhood function we implemented the bubble kernel. (slides page 33)

[comment]: <> (`cluster 1503,1693 P1 2018-01-01 2018-01-05 1h mean 24 2 2 1 0.5 10000 0xCAFE 100`)

[comment]: <> (`cluster all P1 2018-01-01 2018-01-05 1h mean 24 2 2 1 0.5 10000 0xCAFE 100`)

[comment]: <> (`/docker/client.sh cluster 1779,2043,7819,3735,3841,3891,3993,4021,4430,4963,5734,5819,6493,7263,7440,7645 P1 2018-01-01 2018-01-05 1h mean 24 2 2 1 0.5 10000 0xCAFE 100`)

[comment]: <> ( `./docker/client.sh cluster 1503,1841,4578,5735,6218,6318,7133,7496,7819 temperature 2018-01-01 2018-01-02 1h mean 2 4 4 0.5 0.5 10 0xCAFE 2`)



## `rm` command 
The `rm` command is implemented. It will delete the result folder with all the results. 

## `listresults` and `inspectcluster`
The `listresults` and `inspectcluster` commands are implemented.
We interpreted the instructions the following way: The instructions say to list finished results.
We assumed that only `.json` files ending in `_final.json` count as such finished results. 
Ergo: Only finished results will be listed in `listresults` even when there are already intermediate results existing. 
The same goes for `inspectcluster`.

The `inspectcluster` command has been implemented, however, the `verbose=true` option is commented out due to problems with the `gson` functionality. 


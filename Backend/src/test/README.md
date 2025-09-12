to run the test cases 
combines all the test cases to run on junit platform console 

$files = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -d bin -cp "bin;junit-platform-console-standalone-1.9.3.jar" $files

and finally run all tests 
java -jar junit-platform-console-standalone-1.9.3.jar -cp bin --scan-classpath


to run a particular test class:
java -jar junit-platform-console-standalone-1.9.3.jar -cp bin --select-class backend.RaptorTest

just add file path e.g. backend.RaptorTest to run RaptorTest indiviudally 


in raptor deal with max waiting time( walking distance can be waiting time for next possible train????/)



To delete the compiled classes in bin 
javac -d bin src/*.java
Remove-Item bin\* -Recurse -Force



final call to run the entire system 
Transit system will load data in dataloader and run queries as request to the frontend and will finish everything 



THE FINAL OUTCOME 

call TransitSystem.query("Cape Town", "Maitland", "07:30")
- this runs raptor and reconstrucits the path 
- it returns a List<PathStep>

Each Pathstep contains:
- tripid you are on
- Stopname
- stopTime
- The coordinates(lat,long)

what you need to draw the route on map 

maybe wrap everytthing in springboot and sent to frontend??



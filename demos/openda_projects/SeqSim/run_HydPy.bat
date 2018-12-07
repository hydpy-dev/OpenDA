curl --data-binary "@C:\HydPy\OpenDA\demos\openda_projects\SeqSim\results/temp0/hydpy.exchange" http://localhost:8080/period?id=test
curl --data-binary "@C:\HydPy\OpenDA\demos\openda_projects\SeqSim\results/temp0/hydpy.exchange" http://localhost:8080/parametervalues?id=test
curl http://localhost:8080/load_conditions?id=test
curl --data-binary "@C:\HydPy\OpenDA\demos\openda_projects\SeqSim\results/temp0/hydpy.exchange" http://localhost:8080/conditionvalues?id=test
curl http://localhost:8080/simulate?id=test
curl http://localhost:8080/save_conditionvalues?id=test
curl http://localhost:8080/itemvalues?id=test -o C:\HydPy\OpenDA\demos\openda_projects\SeqSim\results/temp0/hydpy.exchange
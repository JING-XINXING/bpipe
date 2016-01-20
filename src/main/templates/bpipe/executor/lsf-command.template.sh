#!/bin/sh
#BSUB -J ${config.jobname?:name}
<%if(memoryMB) {%>
#BSUB -R "rusage[mem=$memoryMB]"
<%}%>
trap 'echo 255 > $jobDir/$CMD_EXIT_FILENAME;' HUP INT TERM QUIT KILL STOP USR1 USR2

cd ${bpipe.Runner.canonicalRunDirectory}

echo \$LSB_JOBINDEX >> ${CMD_EXIT_FILENAME}.jobs

(
bash -e ${CMD_FILENAME.absolutePath}
) > $jobDir/$CMD_OUT_FILENAME

result=\$?
echo -n \$result >> $jobDir/$CMD_EXIT_FILENAME
echo -n \$result > $jobDir/${CMD_EXIT_FILENAME}.\$LSB_JOBINDEX
exit \$result

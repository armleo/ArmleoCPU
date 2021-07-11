FROM armleo/armleocpu_toolset:latest
ARG WORKSPACE
WORKDIR $WORKSPACE
RUN make
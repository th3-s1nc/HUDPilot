FROM python:3.12-slim
ARG PMTILES_VERSION=1.22.1
ARG BROUTER_VERSION=1.7.7
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates osmium-tool default-jre-headless unzip \
 && curl -fsSL -o /tmp/p.tgz https://github.com/protomaps/go-pmtiles/releases/download/v${PMTILES_VERSION}/go-pmtiles_${PMTILES_VERSION}_Linux_x86_64.tar.gz \
 && tar -xzf /tmp/p.tgz -C /usr/local/bin pmtiles && rm /tmp/p.tgz \
 && mkdir -p /opt/brouter && curl -fsSL -o /tmp/b.zip https://github.com/abrensch/brouter/releases/download/v${BROUTER_VERSION}/brouter-${BROUTER_VERSION}.zip \
 && unzip -q /tmp/b.zip -d /tmp/bx && cp /tmp/bx/brouter-${BROUTER_VERSION}/brouter-${BROUTER_VERSION}-all.jar /opt/brouter/brouter.jar && rm -rf /tmp/b.zip /tmp/bx \
 && apt-get purge -y --auto-remove && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY regions.json update.py baustellen.py ./
COPY limits ./limits
COPY static ./static
COPY brouter-profiles /opt/brouter/profiles2
EXPOSE 8085
CMD ["python", "-u", "update.py"]

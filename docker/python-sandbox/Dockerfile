FROM python:3.10-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    MPLCONFIGDIR=/tmp/matplotlib

RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-noto-cjk \
    && pip install --no-cache-dir pandas numpy matplotlib seaborn \
    && rm -rf /var/lib/apt/lists/*

USER 65534:65534
WORKDIR /sandbox

CMD ["python", "--version"]

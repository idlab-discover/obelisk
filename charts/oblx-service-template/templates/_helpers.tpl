{{/*
Expand the name of the chart.
*/}}
{{- define "oblx-service-template.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "oblx-service-template.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}


{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "oblx-service-template.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "oblx-service-template.labels" -}}
helm.sh/chart: {{ include "oblx-service-template.chart" . }}
{{ include "oblx-service-template.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "oblx-service-template.selectorLabels" -}}
app.kubernetes.io/name: {{ include "oblx-service-template.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Default probes (enabled by default)
*/}}
{{- define "oblx-service-template.defaultProbes" -}}
{{- if not .Values.disableDefaultProbes -}}
livenessProbe:
  httpGet:
    path: /_health
    port: http
readinessProbe:
  httpGet:
    path: /_status
    port: http
{{- end }}
{{- end }}

{{- define "oblx-service-template.podAnnotations" -}}
{{- if or .Values.global.rollOnConfigChange .Values.rollOnConfigChange -}}
checksum/globalconfig: {{ include "obelisk.config" . | sha256sum }}
{{- if .Values.config }}
checksum/config: {{ include (print $.Template.BasePath "/config.yaml") . | sha256sum  }}
{{- end }}
{{- end }}
{{- if or .Values.global.recreate .Values.recreate }}
recreate: {{ randAlphaNum 10 | quote }}
{{- end }}
{{- end }}

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
Create the name of the service account to use
*/}}
{{- define "oblx-service-template.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "oblx-service-template.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Default probes (enabled by default)
*/}}
{{- define "oblx-service-template.defaultProbes" -}}
{{- if not .Values.disableDefaultProbes }}
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


{{- define "oblx.commons" -}}
  {{- if .Values.commonsReleaseName }}
    {{- .Values.commonsReleaseName }}
  {{- else }}
    {{- "oblx-commons" }}
  {{- end }}
{{- end }}

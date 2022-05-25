{{- define "oblx-service-template.serviceMonitor" -}}
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: {{ include "oblx-service-template.fullname" . }}
  labels:
    {{- include "oblx-service-template.labels" . | nindent 4 }}
spec:
  endpoints:
  - interval: 30s
    port: metrics
  jobLabel: app.kubernetes.io/name
  selector:
    matchLabels:
    {{- include "oblx-service-template.selectorLabels" . | nindent 6 }}
{{- end -}}

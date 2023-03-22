{{- define "oblx-service-template.service" -}}
apiVersion: v1
kind: Service
metadata:
  name: {{ include "oblx-service-template.fullname" . }}
  labels:
    {{- include "oblx-service-template.labels" . | nindent 4 }}
spec:
  type: {{ .Values.service.type }}
  ports:
    - port: {{ .Values.service.port }}
      targetPort: http
      protocol: TCP
      name: http
    - port: 8081
      targetPort: metrics
      protocol: TCP
      name: metrics
  selector:
    {{- include "oblx-service-template.selectorLabels" . | nindent 4 }}
{{- end -}}

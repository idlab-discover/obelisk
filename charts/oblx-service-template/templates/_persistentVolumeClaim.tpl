{{- define "oblx-service-template.persistentVolumeClaim" -}}
{{- if .Values.persistence }}
kind: PersistentVolumeClaim
apiVersion: v1
metadata:
  name: {{ include "oblx-service-template.fullname" . }}-pv-claim
spec:
  storageClassName: {{ default .Values.global.storageClassName .Values.persistence.storageClassName }}
  accessModes:
    - {{ .Values.persistence.accessMode }}
  resources:
    requests:
      storage: {{ .Values.persistence.requestSize }}
{{- end }}
{{- end -}}

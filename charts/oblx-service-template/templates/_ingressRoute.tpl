{{- define "oblx-service-template.ingressRoute" -}}
{{- $fullName := include "oblx-service-template.fullname" . -}}
{{- $svcPort := .Values.service.port -}}
{{- $host := default .Values.global.config.host .Values.host -}}
apiVersion: traefik.containo.us/v1alpha1
kind: IngressRoute
metadata:
  name: {{ $fullName }}-route
spec:
  {{- if .Values.tls.enabled }}
  entryPoints:
  - websecure
  tls:
    {{- if .Values.tls.secretName }}
    secretName: {{ .Values.tls.secretName }}
    {{- else if .Values.tls.certResolver }}
    certResolver: {{ .Values.tls.certResolver }}
    {{- end }}
  {{- else }}
  entryPoints:
  - web
  {{- end }}
  routes:
  - match: Host(`{{ $host }}`) && PathPrefix(`{{ $.Values.basePath }}`)
    kind: Rule
    services:
    - name: {{ $fullName }}
      port: {{ $svcPort }}
{{- end -}}

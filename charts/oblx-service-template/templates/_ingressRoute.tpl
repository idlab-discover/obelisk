{{- define "oblx-service-template.ingressRoute" -}}
{{- $fullName := include "oblx-service-template.fullname" . -}}
{{- $svcPort := .Values.service.port -}}
apiVersion: traefik.containo.us/v1alpha1
kind: IngressRoute
metadata:
  name: {{ $fullName }}-route
spec:
  entryPoints:
  - web
  routes:
  - match: PathPrefix(`{{ $.Values.basePath }}`)
    kind: Rule
    services:
    - name: {{ $fullName }}
      port: {{ $svcPort }}
{{- end -}}

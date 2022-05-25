{{- define "oblx-service-template.deployment" -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "oblx-service-template.fullname" . }}
  labels:
    {{- include "oblx-service-template.labels" . | nindent 4 }}
spec:
  {{- if not .Values.autoscaling.enabled }}
  replicas: {{ .Values.replicaCount }}
  {{- end }}
  selector:
    matchLabels:
      {{- include "oblx-service-template.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      {{- with .Values.podAnnotations }}
      annotations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      labels:
        {{- include "oblx-service-template.selectorLabels" . | nindent 8 }}
    spec:
      {{- with .Values.imagePullSecrets }}
      imagePullSecrets:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      securityContext:
        {{- toYaml .Values.podSecurityContext | nindent 8 }}
      volumes:
        - name: logback
          configMap:
            name: "{{ .Values.oblxCommonsChartName }}-log-config"
      {{- if .Values.persistence }}
        - name: {{ include "oblx-service-template.name" . }}-data
          persistentVolumeClaim:
            claimName: {{ include "oblx-service-template.fullname" . }}-pv-claim
      {{- end }}
      containers:
        - name: {{ .Chart.Name }}
          securityContext:
            {{- toYaml .Values.securityContext | nindent 12 }}
          image: "{{ .Values.image.repository }}/{{ .Values.image.name }}:{{ .Values.image.tag | default .Chart.AppVersion }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: 8080
              protocol: TCP
            - name: metrics
              containerPort: 8081
              protocol: TCP
          env:
            - name: JAVA_TOOL_OPTIONS
              value: "-XX:InitialRAMPercentage=40 -XX:MaxRAMPercentage=80"
          envFrom:
          {{- if required "Value 'oblxCommonsChartName' is required!" .Values.oblxCommonsChartName }}
            - configMapRef:
                name: "{{ .Values.oblxCommonsChartName }}-config"
          {{- end }}
          {{- if .Values.config }}
            - configMapRef:
                name: {{ include "oblx-service-template.fullname" . }}-config
          {{- end }}
          {{- include "oblx-service-template.defaultProbes" . | nindent 10 }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
          volumeMounts:
            - mountPath: /app/config
              name: logback
          {{- if .Values.persistence }}
            - mountPath: {{ .Values.persistence.mountPath }}
              name: {{ include "oblx-service-template.name" . }}-data
          {{- end }}
      {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.affinity }}
      affinity:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
{{- end }}

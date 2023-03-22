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
      annotations:
        {{- include "oblx-service-template.podAnnotations" . | nindent 8 }}
      {{- with .Values.podAnnotations }}
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
            name: "{{ include "oblx-service-template.fullname" . }}-log-config"
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
              containerPort: {{ .Values.containerPort}}
              protocol: TCP
          {{- if .Values.metrics.enabled }}
            - name: metrics
              containerPort: {{ .Values.metrics.port }}
              protocol: TCP
          {{- end }}
          env:
          {{- if .Values.basePath }}
            - name: HTTP_BASE_PATH
              value: {{ .Values.basePath }}
          {{- end }}
            - name: JAVA_TOOL_OPTIONS
              value: "-XX:InitialRAMPercentage=40 -XX:MaxRAMPercentage=80"
          envFrom:
          {{- if .Values.global }}
            {{- if .Values.global.config }}
            - configMapRef:
                name: {{ .Release.Name}}-global-config
            {{- end }}
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
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ include "oblx-service-template.fullname" . }}-log-config
  labels:
  {{- include "oblx-service-template.labels" . | nindent 4 }}
data:
  logback.xml: |-
    <?xml version="1.0" encoding="UTF-8"?>
    <configuration>
      <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
          <pattern>%d{HH:mm:ss.SSS} | %-5level | %logger{1} | %m%n%rEx{3,
              org.codejargon.feather,
              io.netty,
              io.reactivex
              }
          </pattern>
        </encoder>
      </appender>
      <root level="warn">
          <appender-ref ref="STDOUT"/>
      </root>
      <logger name="idlab" level="{{ .Values.logLevel }}"/>
    </configuration>
{{- end -}}

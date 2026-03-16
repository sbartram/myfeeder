{{/*
Expand the name of the chart.
*/}}
{{- define "myfeeder.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "myfeeder.fullname" -}}
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
Common labels
*/}}
{{- define "myfeeder.labels" -}}
helm.sh/chart: {{ include "myfeeder.name" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
App selector labels
*/}}
{{- define "myfeeder.appSelectorLabels" -}}
app.kubernetes.io/name: {{ include "myfeeder.fullname" . }}
app.kubernetes.io/component: app
{{- end }}

{{/*
Postgres selector labels
*/}}
{{- define "myfeeder.postgresSelectorLabels" -}}
app.kubernetes.io/name: {{ include "myfeeder.fullname" . }}
app.kubernetes.io/component: postgres
{{- end }}

{{/*
Redis selector labels
*/}}
{{- define "myfeeder.redisSelectorLabels" -}}
app.kubernetes.io/name: {{ include "myfeeder.fullname" . }}
app.kubernetes.io/component: redis
{{- end }}

variable "aws_region" {
  description = "Região AWS onde os recursos serão criados"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Ambiente (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "aws_account_id" {
  description = "ID da conta AWS - recuperado do SSM"
  type        = string
  sensitive   = true
}

variable "project_name" {
  description = "Nome do projeto (não sensível)"
  type        = string
  default     = "process-video"
}

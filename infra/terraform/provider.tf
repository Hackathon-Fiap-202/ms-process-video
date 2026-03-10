terraform {
  required_version = ">= 1.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "nextime-food-state-bucket"
    key            = "process-video/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment  = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# Usar aws_account_id do SSM Parameter Store
locals {
  aws_account_id = data.aws_ssm_parameter.aws_account_id.value
}


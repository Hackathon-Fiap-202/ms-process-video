data "terraform_remote_state" "network" {
  backend = "s3"
  config = {
    bucket = "nextime-food-state-bucket"
    key    = "infra-core/infra.tfstate"
    region = "us-east-1"
  }
}

data "terraform_remote_state" "sqs" {
  backend = "s3"
  config = {
    bucket = "nextime-food-state-bucket"
    key    = "sqs/infra.tfstate"
    region = "us-east-1"
  }
}

data "terraform_remote_state" "kubernetes" {
  backend = "s3"
  config = {
    bucket = "nextime-food-state-bucket"
    key    = "infra-kubernetes/cluster.tfstate"
    region = "us-east-1"
  }
}

# Recuperar AWS Account ID do SSM Parameter Store
# Este parâmetro é gerenciado por outro repositório
# Nome esperado: /process-video/terraform/aws-account-id
data "aws_ssm_parameter" "aws_account_id" {
  name = "/process-video/terraform/aws-account-id"
}

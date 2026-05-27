# KVStore Distribuído — Servidor Java (gRPC + MQTT + Vector Clocks)

Implementação **compatível com o cliente oficial**:  
<https://github.com/paulo-coelho/2025-1-kvstore-client>

## Requisitos
- Java 17+
- Maven 3.8+
- Broker MQTT (ex.: **mosquitto**)
- Cargo (Rust) para compilar e usar o cliente oficial

### Instalar Mosquitto (Ubuntu/Debian)
```bash
sudo apt-get update
sudo apt-get install -y mosquitto
mosquitto -v    # inicia broker local (porta 1883)
```
### Instalar Mosquitto (Windows)
O gerenciador de pacotes `winget` nativo do Windows 11 é a forma mais rápida de instalar o broker pelo terminal (PowerShell ou Prompt de Comando):
```bash
winget install EclipseFoundation.Mosquitto
```
*(Alternativamente, baixe o instalador `.exe` em https://mosquitto.org/download/)*.
Após instalar, navegue até a pasta de instalação (geralmente `C:\Program Files\mosquitto`) e digite `mosquitto -v` para iniciar o broker.

## Compilar
```bash
./compile.sh
```

**Para Windows (utilizando o terminal Git Bash):**
```bash
bash compile.sh
```

## Executar
Em dois terminais diferentes (com o broker rodando):

```bash
# Nó A
./server.sh --node-id node_A --listen-addr 127.0.0.1:50051             --mqtt-broker-addr 127.0.0.1 --mqtt-broker-port 1883

# Nó B
./server.sh --node-id node_B --listen-addr 127.0.0.1:50052             --mqtt-broker-addr 127.0.0.1 --mqtt-broker-port 1883
```

**Para Windows (utilizando o terminal Git Bash):**
```bash
# Nó A
bash server.sh --node-id node_A --listen-addr 127.0.0.1:50051          --mqtt-broker-addr 127.0.0.1 --mqtt-broker-port 1883

# Nó B
bash server.sh --node-id node_B --listen-addr 127.0.0.1:50052          --mqtt-broker-addr 127.0.0.1 --mqtt-broker-port 1883
```

## Testar com o Cliente Oficial
Clone e compile o cliente oficial:

```bash
git clone https://github.com/paulo-coelho/2025-1-kvstore-client
cd 2025-1-kvstore-client
cargo build
```

Exemplo de uso:

```bash
# PUT em A
./target/debug/kv_client 127.0.0.1:50051 put chave1 valorA

# GET em B (replicado)
./target/debug/kv_client 127.0.0.1:50052 get chave1
```

### Casos Esperados
- **Replicação:** GET em B retorna `valorA`.
- **Concorrência:** dois PUTs quase simultâneos em A e B na mesma chave → GET retorna **duas versões**.
- **Substituição:** uma versão que sucede outra substitui a anterior.

## Testes Automatizados com `test_kvstore.sh`
O projeto inclui o script `test_kvstore.sh` para validar a execução ponta a ponta com o cliente oficial.

### Pré-requisitos
- Broker **mosquitto** rodando (`mosquitto -v`).
- Dois nós do servidor ativos (como mostrado acima).
- Cliente oficial clonado e compilado.

### Executar os testes
No diretório do cliente oficial:

```bash
chmod +x ../kvstore-server/test_kvstore.sh
../kvstore-server/test_kvstore.sh
```

**Para Windows (utilizando o terminal Git Bash):**
```bash
bash ../kvstore-server/test_kvstore.sh
```

### O que o script valida
1. **Replicação básica:** PUT em A, GET em B → valor idêntico.  
2. **Conflito:** PUT simultâneo em A e B na mesma chave → GET mostra duas versões concorrentes.  
3. **Substituição:** PUT v1 seguido de v2 no mesmo nó → GET retorna apenas a versão mais recente.  

Logs detalhados de cada execução ficam em `/tmp/*.out`.

## Arquitetura
- **gRPC**: serviço `KvStore` com métodos `Put` e `Get` (veja `src/main/proto/kvstore.proto`).  
- **Replicação via MQTT**: tópico `kvstore/replication` (QoS 1), payload em JSON com a chave e todas as versões ativas.  
- **Vector Clocks**: resolução de conflitos, mantendo múltiplas versões apenas quando são concorrentes.  

## Scripts
- `compile.sh`: compila e empacota o JAR executável.  
- `server.sh`: executa o servidor com os argumentos necessários.  
- `test_kvstore.sh`: roda testes automáticos de replicação, concorrência e substituição.  

# Sprint 1: servidor de desenvolvimento do Vite, suficiente para demonstracao do PI.
# Um Dockerfile de producao com build estatico + Nginx fica para a Sprint 5 (Polish e Deploy).
FROM node:20-alpine
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install

COPY . .

EXPOSE 5173
CMD ["npm", "run", "dev"]

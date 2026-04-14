Las credenciales del admin son:
Email: admin@pucmm.edu.do
Password: Admin1234!

Debe tener un archivo .env en la raiz del proyecto, es decir, al mismo nivel que se encuentra el Dockerfile y el docker-compose-yml
El archivo .env debe contener:
PORT=8000
JWT_SECRET=<reemplazar esto sin comillas>
MONGO_URI=<Reemplazar esto sin comillas>

import { useState, useRef, type FormEvent } from 'react';
import { Mail, User as UserIcon, Camera, Trash2 } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { Avatar } from '../ui/Avatar';
import { Alert } from '../ui/Alert';
import { useAuth } from '../../hooks/useAuth';
import { userService } from '../../services/userService';
import { ApiError } from '../../services/ApiError';

/** Taille de la photo enregistrée (carré, en pixels) : assez petite pour tenir dans users.avatar_url. */
const TAILLE_AVATAR = 128;

/** Redimensionne l'image en carré JPEG et renvoie une data URL compacte. */
function redimensionner(fichier: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const lecteur = new FileReader();
    lecteur.onerror = () => reject(new Error('Lecture du fichier impossible.'));
    lecteur.onload = () => {
      const image = new Image();
      image.onerror = () => reject(new Error('Image illisible.'));
      image.onload = () => {
        const cote = Math.min(image.width, image.height);
        const canvas = document.createElement('canvas');
        canvas.width = TAILLE_AVATAR;
        canvas.height = TAILLE_AVATAR;
        const contexte = canvas.getContext('2d');
        if (!contexte) {
          reject(new Error('Traitement de l’image impossible.'));
          return;
        }
        contexte.drawImage(image, (image.width - cote) / 2, (image.height - cote) / 2, cote, cote,
          0, 0, TAILLE_AVATAR, TAILLE_AVATAR);
        resolve(canvas.toDataURL('image/jpeg', 0.85));
      };
      image.src = lecteur.result as string;
    };
    lecteur.readAsDataURL(fichier);
  });
}

export function ProfileSection() {
  const { user, setUser } = useAuth();
  const [name, setName] = useState(user?.name ?? '');
  const [email, setEmail] = useState(user?.email ?? '');
  const [avatar, setAvatar] = useState<string | null>(user?.avatarUrl ?? null);
  const [isSaving, setIsSaving] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Le profil peut arriver après l'ouverture de la page : on resynchronise le formulaire.
  const [utilisateurAffiche, setUtilisateurAffiche] = useState(user);
  if (user !== utilisateurAffiche) {
    setUtilisateurAffiche(user);
    if (user) {
      setName(user.name);
      setEmail(user.email);
      setAvatar(user.avatarUrl ?? null);
    }
  }

  const enregistrer = async (payload: { name?: string; email?: string; avatarUrl?: string }, message: string) => {
    setIsSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const misAJour = await userService.updateMe(payload);
      setUser(misAJour);
      setSuccess(message);
      setTimeout(() => setSuccess(null), 3000);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Une erreur est survenue.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleChangePhoto = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      setError('Veuillez sélectionner une image.');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setError('Image trop lourde (max 5 Mo).');
      return;
    }
    try {
      const dataUrl = await redimensionner(file);
      setAvatar(dataUrl);
      await enregistrer({ avatarUrl: dataUrl }, 'Votre photo de profil a été mise à jour.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Image invalide.');
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleRemovePhoto = async () => {
    setAvatar(null);
    // Une chaîne vide efface la photo côté backend.
    await enregistrer({ avatarUrl: '' }, 'Votre photo de profil a été supprimée.');
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    await enregistrer({ name: name.trim(), email: email.trim() }, 'Vos informations ont été mises à jour.');
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Profil</h2>
        <p className="text-sm text-muted-foreground">
          Gérez vos informations personnelles.
        </p>
      </div>

      {success && <Alert variant="success">{success}</Alert>}
      {error && <Alert variant="danger">{error}</Alert>}

      <Card>
        <CardHeader>
          <CardTitle>Photo de profil</CardTitle>
          <CardDescription>
            Cette photo apparaîtra sur votre profil.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center gap-4">
            {avatar ? (
              <img
                src={avatar}
                alt="Photo de profil"
                className="h-12 w-12 rounded-full object-cover"
              />
            ) : (
              <Avatar name={name || 'Utilisateur'} size="lg" />
            )}

            <div className="flex gap-2">
              {/* Input caché pour ouvrir l'explorateur */}
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={handleFileChange}
              />

              <Button
                variant="outline"
                size="sm"
                leftIcon={<Camera className="h-4 w-4" />}
                onClick={handleChangePhoto}
                disabled={isSaving}
              >
                Changer
              </Button>

              {avatar && (
                <Button
                  variant="ghost"
                  size="sm"
                  leftIcon={<Trash2 className="h-4 w-4" />}
                  onClick={handleRemovePhoto}
                  disabled={isSaving}
                >
                  Supprimer
                </Button>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Informations personnelles</CardTitle>
          <CardDescription>
            Mettez à jour vos informations de compte.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <Input
              label="Nom complet"
              name="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              leftIcon={<UserIcon className="h-4 w-4" />}
            />
            <Input
              label="Email"
              name="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              leftIcon={<Mail className="h-4 w-4" />}
            />
            <div className="flex justify-end">
              <Button type="submit" isLoading={isSaving}>
                Enregistrer
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

import { useState, useRef, type FormEvent } from 'react';
import { Mail, User as UserIcon, Camera, Trash2 } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { Input } from '../ui/Input';
import { Textarea } from '../ui/Textarea';
import { Button } from '../ui/Button';
import { Avatar } from '../ui/Avatar';
import { Alert } from '../ui/Alert';

export function ProfileSection() {
  const [name, setName] = useState('Rickael Brayan');
  const [email, setEmail] = useState('rickael@company.com');
  const [bio, setBio] = useState('');
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [success, setSuccess] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Ouvre l'explorateur de fichiers
  const handleChangePhoto = () => {
    fileInputRef.current?.click();
  };

  // Gère la sélection du fichier
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Vérifie le type
    if (!file.type.startsWith('image/')) {
      alert('Veuillez sélectionner une image.');
      return;
    }

    // Vérifie la taille (max 2 Mo)
    if (file.size > 2 * 1024 * 1024) {
      alert('Image trop lourde (max 2 Mo).');
      return;
    }

    // Aperçu local
    const reader = new FileReader();
    reader.onload = (event) => {
      setAvatarPreview(event.target?.result as string);
    };
    reader.readAsDataURL(file);

    // Plus tard : envoyer au backend
    // await userService.uploadAvatar(file);
  };

  const handleRemovePhoto = () => {
    setAvatarPreview(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setIsSaving(true);
    setSuccess(false);
    await new Promise((r) => setTimeout(r, 600));
    setIsSaving(false);
    setSuccess(true);
    setTimeout(() => setSuccess(false), 3000);
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Profil</h2>
        <p className="text-sm text-muted-foreground">
          Gérez vos informations personnelles.
        </p>
      </div>

      {success && (
        <Alert variant="success">
          Vos informations ont été mises à jour.
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Photo de profil</CardTitle>
          <CardDescription>
            Cette photo apparaîtra sur votre profil et vos commentaires.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center gap-4">
            {avatarPreview ? (
              <img
                src={avatarPreview}
                alt="Aperçu"
                className="h-12 w-12 rounded-full object-cover"
              />
            ) : (
              <Avatar name={name} size="lg" />
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
              >
                Changer
              </Button>

              {avatarPreview && (
                <Button
                  variant="ghost"
                  size="sm"
                  leftIcon={<Trash2 className="h-4 w-4" />}
                  onClick={handleRemovePhoto}
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
            <Textarea
              label="Bio"
              name="bio"
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              placeholder="Quelques mots sur vous..."
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